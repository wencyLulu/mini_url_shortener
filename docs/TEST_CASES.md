# 测试用例清单（固化）

本文件是 URL Shortener 的**验收基线**：每一条需求都对应到具体用例、具体断言与具体代码位置。
用例分两层：

1. **自动化用例**（`./mvnw verify`，27 个）——随代码提交，CI 可重复执行，是主要验收依据。
2. **手工冒烟脚本**（`scripts/smoke-test.sh`）——对一个已启动的实例做端到端 curl 验证，适合人工验收。

## 固化规则

| 规则 | 做法 |
|---|---|
| 用例互不依赖 | 每个集成用例执行前清空数据库（`@BeforeEach` → `linkRepository.deleteAll()`，`link_daily_visit` 级联删除），缓存用例同时清空 Caffeine |
| 不依赖本机时区 | 所有"今天/当前时间"来自注入的 `Clock.systemUTC()`；统计按 UTC 日切分 |
| 不依赖网络 | 重定向只断言 302 与 `Location` 头，MockMvc 与 HttpClient 均配置为不跟随跳转 |
| 可重复执行 | `./mvnw clean verify` 多次运行结果一致；数据落在独立内存库（`jdbc:h2:mem:<name>`） |
| 并发可复现 | 用真实 HTTP + 固定线程数（32×20）压测，断言计数**精确等于**期望值而非范围 |

---

## 一、创建短链（POST /api/links）

| ID | 场景 | 步骤 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-001 | 正常创建 | POST `{"url":"https://example.com/..."}` | 201；`code` 为 7 位 `[A-Za-z0-9]`；`short_url` = `{baseUrl}/{code}`；`created_at` 存在；`expires_at` 为 null | `createRedirectDetailsAndStats`、`createReturnsShortUrlAndCreatedAt` |
| TC-002 | 同一 URL 重复提交 | 用同一 URL 创建两次 | 两次均 201，且**两个 code 不同**；两个 code 都能正确重定向 | `sameUrlSubmittedTwiceGetsTwoDifferentCodes` |
| TC-003 | 自定义短码 | POST 带 `code: "myalias"` | 201，返回 code 为 `myalias`；`GET /myalias` → 302 | `customAliasIsHonouredAndRejectedWhenTaken` |
| TC-004 | 自定义短码冲突 | 再次提交已占用的 `myalias` | **409**，`error=CODE_ALREADY_EXISTS` | `customAliasIsHonouredAndRejectedWhenTaken` |
| TC-005 | 短码过短 | `code: "ab"` | **400**，`error=INVALID_CODE` | `invalidCustomAliasIsRejected` |
| TC-006 | 短码含非法字符 | `code: "ab!"` | **400**，`error=INVALID_CODE` | `aliasWithIllegalCharacterIsRejected` |
| TC-007 | 短码占用保留字 | `code: "api"` | **400**，`error=INVALID_CODE` | `invalidCustomAliasIsRejected` |
| TC-008 | URL 缺失/为空 | `{}`、`{"url":""}` | **400**，`error=INVALID_URL` | `invalidUrlsAreRejected` |
| TC-009 | URL 无协议 | `not-a-url` | **400**，`error=INVALID_URL` | `invalidUrlsAreRejected` |
| TC-010 | 非 http/https 协议 | `ftp://example.com` | **400**，`error=INVALID_URL` | `invalidUrlsAreRejected` |
| TC-011 | URL 无主机 | `https://` | **400**，`error=INVALID_URL` | `invalidUrlsAreRejected` |
| TC-012 | URL 超长 | 长度 > 2048 | **400**，`error=INVALID_URL` | `invalidUrlsAreRejected`、`UrlValidatorTest.rejectsTooLongUrl` |
| TC-013 | 请求体非法 JSON | `{not json` | **400**，`error=MALFORMED_JSON` | `malformedJsonIsRejected` |
| TC-014 | 请求体为空 | 无 body | **400**，`error=MALFORMED_JSON` | `emptyBodyIsRejected` |
| TC-015 | 过期时间在过去 | `expires_at = 2020-01-01T00:00:00Z` | **400**，`error=INVALID_EXPIRY` | `expiryInThePastIsRejected` |
| TC-016 | 错误体格式统一 | 任一 4xx | `{status, error, message, timestamp, path}` 五个字段齐全，`path` 为请求路径 | `assertInvalidUrl()`、`unknownCodeReturnsNotFoundOnEveryEndpoint` |
| TC-017 | 方法不允许 | `DELETE /api/links/{code}` | **405**，`error=METHOD_NOT_ALLOWED` | `unsupportedMethodReturns405` |

## 二、重定向（GET /{code}）

| ID | 场景 | 步骤 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-018 | 正常重定向 | GET `/{code}` | **302**；`Location` = 原始长链；`Cache-Control: no-store`（不为 301，避免浏览器永久缓存导致统计漏计） | `createRedirectDetailsAndStats` |
| TC-019 | 未知短码 | GET `/zzzzzzz` | **404**，`error=NOT_FOUND` | `unknownCodeReturnsNotFoundOnEveryEndpoint` |
| TC-020 | 链接已过期 | 写入 `expires_at` 为过去的链接后 GET | **410**，`error=LINK_EXPIRED` | `expiredLinkIsGoneButStillInspectable` |
| TC-021 | 静态资源不被误判为短码 | GET `/`、`/index.html` | 200 返回 HTML 页面，而非 404 | `rootServesTheMinimalUi` |

## 三、链接详情（GET /api/links/{code}）

| ID | 场景 | 步骤 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-022 | 查看详情 | 创建后访问 1 次再查详情 | 200；含 `code`、`url`、`created_at`、`visit_count=1` | `createRedirectDetailsAndStats` |
| TC-023 | 未知短码 | 详情接口查未知码 | **404**，`error=NOT_FOUND` | `unknownCodeReturnsNotFoundOnEveryEndpoint` |
| TC-024 | 过期链接仍可查询 | 已过期链接查详情 | 200，且 `expires_at` 有值（只有重定向被禁用） | `expiredLinkIsGoneButStillInspectable` |

## 四、访问统计（GET /api/links/{code}/stats）

| ID | 场景 | 步骤 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-025 | 7 日窗口 | 访问 1 次后查统计 | `days` 长度**恰为 7**；`days[0]` = 今天-6；`days[6]` = 今天 | `createRedirectDetailsAndStats` |
| TC-026 | 升序 + 补零 | 构造"今天-1: 5 次、今天-3: 7 次" | 顺序为最旧→最新；无访问的日期 `count=0` | `LinkStatsWindowTest.windowContainsLastSevenDaysOnlyAndFillsMissingDaysWithZero` |
| TC-027 | 窗口边界 | 埋入"今天-8: 99 次"、"今天-6: 2 次" | 今天-6 **在**窗口内；今天-8 **不在**窗口内 | `LinkStatsWindowTest` |
| TC-028 | 未知短码 | 统计接口查未知码 | **404**，`error=NOT_FOUND` | `unknownCodeReturnsNotFoundOnEveryEndpoint` |

## 五、并发正确性

| ID | 场景 | 步骤 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-029 | 并发重定向不丢计数 | 32 线程 × 20 次 = 640 次真实 HTTP 重定向 | 全部返回 302；`visit_count` **= 640**；今日桶 **= 640**（精确等于，非区间） | `RedirectConcurrencyTest.noVisitIsLostWhenManyClientsRedirectAtOnce` |
| TC-030 | 计数为 SQL 原子自增 | 代码审查 | 使用 `SET count = count + 1`，不在 Java 中读-改-写；`links` 行锁串行化同链接并发 | README §3.4 |

## 六、缓存

| ID | 场景 | 步骤 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-031 | 热链缓存命中 | 解析同一短码两次 | 第二次命中 `codeToTarget` 缓存，返回同一 `linkId` | `LinkTargetCacheTest.targetIsCachedAndEvictedWhenTheLinkExpires` |
| TC-032 | 过期时失效 | 链接过期后解析 | 命中过期即 `@CacheEvict`，缓存条目被清除 | `LinkTargetCacheTest` |
| TC-033 | 缓存不污染统计 | 并发压测期间缓存生效 | 计数仍精确（缓存只存不可变的 code→url，不含计数） | `RedirectConcurrencyTest` + README §3.6 |

## 七、列表与页面（加分）

| ID | 场景 | 步骤 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-034 | 最新优先分页 | 依次创建 two、one 后 `GET /api/links?size=50` | `content` 按 `created_at DESC, id DESC`；`total_elements=2`；`total_pages=1`；`page=0` | `listingReturnsNewestLinksFirst` |
| TC-035 | 首页可访问 | GET `/` | 200，返回含 "Mini URL Shortener" 的 HTML | `rootServesTheMinimalUi` |

## 八、单元测试

| ID | 场景 | 预期 | 自动化位置 |
|---|---|---|---|
| TC-036 | 短码长度/字母表/唯一性 | 长度 7；仅 `[A-Za-z0-9]`；20 000 次生成无重复 | `Base62CodeGeneratorTest` |
| TC-037 | URL 校验各分支 | http/https 通过；空、无协议、非 http、无 host、超长均抛 `InvalidUrlException` | `UrlValidatorTest` |

---

## 执行方式

```bash
# 自动化（27 个用例）
./mvnw clean verify

# 手工冒烟（需先启动服务）
./mvnw spring-boot:run                       # 终端 A
BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh   # 终端 B
```

冒烟脚本覆盖 TC-001、003、004、005、008、013、018、019、020、022、025、029，
失败时以非零退出码结束并打印 `FAIL` 明细。
