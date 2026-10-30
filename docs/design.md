# Mini URL Shortener — 设计文档

## 1. 目标与范围

实现 Java 17 / Spring Boot 3.2.1 的短链服务，四个核心能力：创建短链、重定向、查询详情、查询近 7 日访问量。
额外实现全部四项加分功能：自定义短码、链接过期、Caffeine 热链缓存、Docker + 最小前端页。

不在范围内：认证鉴权、多租户、水平扩展（仅在 README 中给出扩展思路）。

## 2. 架构分层

```
HTTP 层    LinkController (/api/links/**)      RedirectController (/{code})   IndexController (/)
DTO 层     CreateLinkRequest / LinkResponse / LinkDetailsResponse / LinkStatsResponse / ErrorResponse
服务层     LinkService（创建·详情·统计·列表·缓存目标查询）
           RedirectService（解析 + 触发计数）
           VisitRecorder（计数编排，冲突时重放）  VisitCounter（单事务原子自增）
           LinkStore（独立事务插入，支撑短码冲突重试）
           CodeGenerator（Base62 随机码）  CodePolicy / UrlValidator
仓储层     LinkRepository / LinkDailyVisitRepository (Spring Data JPA)
存储       H2 文件库 ./data/urlshortener，Flyway 版本化迁移
```

规则：controller 只做协议转换与参数绑定，业务逻辑全部在 service；异常统一由 `@RestControllerAdvice` 转为
同一形状的 JSON 错误体。

## 3. 数据模型

- `links(id, code UNIQUE, url, created_at, expires_at, visit_count, last_visited_at)`
- `link_daily_visit(id, link_id, visit_day, visit_count, UNIQUE(link_id, visit_day))`

`visit_count` 是总量（冗余），日表是 7 日窗口来源。

## 4. 五项设计决策（README 同源）

### 4.1 短码生成：随机 Base62，7 位，冲突重试
- 62^7 ≈ 3.5×10^12 空间；`SecureRandom` 生成，字符集 `[0-9A-Za-z]`。
- 为什么不用自增 ID + Base62：短码可枚举（泄露总量、可被遍历抓取），且需要先落库拿 ID。
- 为什么不用 hash(URL) 截断：与"重复 URL 每次生成新码"的策略冲突（同一 URL 必然同 hash），且需要额外 salt。
- 冲突处理：依赖 `code` 唯一约束兜底。捕获 `DataIntegrityViolationException` 后重新生成并重试，上限 10 次；
  每次插入放在 `REQUIRES_NEW` 独立事务中，避免约束异常把外层事务标记为 rollback-only。仍失败则返回 `503`。

### 4.2 重定向状态码：302 Found
- 301 会被浏览器**永久缓存**，后续访问不再打到服务端 → 访问统计严重漏计，与"统计必须准确"直接冲突。
- 302 每次都回源，计数准确；代价是每次多一次 RTT（可用 CDN/边缘缓存 + 日志回收弥补，超出本题范围）。
- 额外加 `Cache-Control: no-store`，防止共享缓存重放响应。

### 4.3 重复 URL 策略：每次生成新短码
- 理由：不同投放渠道/活动可独立计数与归因；创建路径无需"查询→插入"的 upsert 竞态，写入更轻。
- 代价：存储与 URL 不收敛，无法做全局去重统计。缓解：`url` 列不建索引（本策略下从不按 url 查询，建了纯属浪费）。

### 4.4 日统计存储：按天聚合表（非访问事件日志）
- 聚合表：行数上限 = 链接数 × 天数，读 7 日窗口是 O(7)；事件日志在读多写多的场景下会无界膨胀。
- 代价：丢失单次访问明细（无法按小时切片、无法去重 UV、无法换时区重算）。
- 写入原子性：`UPDATE ... SET visit_count = visit_count + 1 WHERE ...`，运算在数据库单行锁上完成，
  不是 Java 里的读-改-写。两个计数器写在**同一个短事务**中，且**先**执行 `UPDATE links WHERE id = ?`——
  它持有的行锁会把同一链接的并发重定向串行化，于是"今日桶不存在则创建"这一步几乎不可能发生竞态。
  UPDATE 影响 0 行时以 `count = 1` 建桶；万一冲突（数据库无行级锁），事务整体回滚后重放一次。
- H2 2.2 既不支持 `INSERT ... ON CONFLICT DO UPDATE`，也不支持"先读后写"的原子 `MERGE`（已用探针验证），
  故采用"先更新后插入 + 冲突重放"这一最可移植的做法。
- 日界定时区：**UTC**（`Clock.systemUTC()`，`visit_day` 为 `DATE`），避免部署机器时区影响跨天切分。

### 4.5 索引
| 索引 | 类型 | 理由 |
|---|---|---|
| `links.code` | UNIQUE | 短码唯一性硬保证 + 重定向/详情按码查询的主力路径（O(log n)） |
| `link_daily_visit(link_id, visit_day)` | UNIQUE | 聚合自增命中行锁 + 7 日范围扫描；同时阻止重复日行 |
| `links.created_at` | INDEX (DESC) | 前端列表按创建时间倒序分页 |

`url` 无索引（见 4.3）。

## 5. 缓存设计（加分项）
- Caffeine：`code -> LinkTarget(id, url, expiresAt)`，`maximumSize=10000`、`expireAfterWrite=10m`。
- 只缓存"码→目标"的不可变映射，**不缓存计数**，因此统计不受缓存影响。
- 失效：短码映射无更新/删除接口，TTL 足够；链接过期时显式 `@CacheEvict`；若未来增加改链/删链接口，
  必须在同一事务提交后显式 evict（否则读到旧目标）。

## 6. 错误处理
统一错误体：`{"status","error","message","timestamp","path"}`。
错误码：`INVALID_URL` 400 / `INVALID_CODE` 400 / `INVALID_EXPIRY` 400 / `MALFORMED_JSON` 400 /
`NOT_FOUND` 404 / `CODE_ALREADY_EXISTS` 409 / `LINK_EXPIRED` 410 / `METHOD_NOT_ALLOWED` 405 /
`CODE_ALLOCATION_FAILED` 503 / `INTERNAL_ERROR` 500。

## 7. 测试策略（TDD）
- 集成测试：MockMvc 跑通 create → redirect → details → stats 全链路，覆盖 404/400/409/410/405 与列表分页。
- 并发测试：
  - 读路径 32 线程 × 20 次 = 640 次真实 HTTP 重定向，断言 `visit_count` 与今日桶精确等于 640；
  - 写路径 16 线程抢同一自定义短码，断言恰好 1 个 201 + 15 个 409；16 线程并发创建，断言短码互不相同。
- 单元测试：短码生成（长度/字符集/2 万次无重复）、URL 校验各分支。
- 窗口测试：注入固定数据，断言 7 日窗口边界、升序、补零。
- 缓存测试：命中与链接过期时的显式失效。
- 每个集成用例在 `@BeforeEach` 中重置数据库，结果不依赖执行顺序。
