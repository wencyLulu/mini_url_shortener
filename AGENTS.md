# AGENT.MD

本文件是给**接手这份代码的人或 AI**看的项目总览与工作约定。读完这一页即可开工。

## 1. 这是什么

用 **Java 17 + Spring Boot 3.2.1** 实现的短链服务：生成短码 → 访问时 302 重定向到原始链接 →
按天统计访问量。存储用内嵌 **H2 文件库**（`./data/urlshortener`，启动时由 **Flyway** 自动建表），
**零外部依赖**。

四个核心能力：创建短链、`GET /{code}` 重定向并计数、查询详情、查询近 7 日访问量。
四项加分功能：自定义短码、`expires_at` 过期（410）、Caffeine 热链缓存、Docker + 最小前端页。

## 2. 30 秒启动

```bash
./mvnw spring-boot:run          # Linux / macOS；Windows 用 mvnw.cmd
# 打开 http://localhost:8080/
```

```bash
./mvnw clean verify                                     # 编译 + 29 个测试
./mvnw -DskipTests package                              # 打包 target/url-shortener-1.0.0.jar
java -jar target/url-shortener-1.0.0.jar
docker compose up --build                               # 另一条一键启动路径
```

端口被占用时：`SERVER_PORT=18080 ./mvnw spring-boot:run`。
要让返回的 `short_url` 带上真实域名：`APP_BASE_URL=https://s.example.com`。

## 3. 目录

```
AGENT.MD                本文件：总览 + 工作约定
README.md               交付说明（中文）：启动、API、5 项设计决策、扩展思路
docs/
  spec.md               原始需求规范
  design.md             设计文档：架构、数据模型、5 项决策及取舍、缓存、错误码
  task.md               任务清单：T1–T8 完成状态与与原计划的偏差
  TEST_CASES.md         验收基线：39 条编号用例（TC-001 … TC-039）
scripts/smoke-test.sh   对运行中实例的 28 项端到端 curl 检查
src/main/java/com/example/urlshortener/
  config/ domain/ repository/ service/ error/ web/
src/main/resources/     application.yml、db/migration/V1__create_schema.sql、static/index.html
src/test/java/          29 个测试
```

分层严格为 **controller → service → repository**：controller 只做协议绑定，业务逻辑在 service，
异常统一由 `@RestControllerAdvice` 转成 `{status, error, message, timestamp, path}`。

## 4. API 速查

| 方法 | 路径 | 结果 |
|---|---|---|
| POST | `/api/links` `{url, code?, expires_at?}` | 201 `{code, short_url, url, created_at, expires_at}` |
| GET | `/{code}` | 302 + `Location`（未命中 404，已过期 410） |
| GET | `/api/links/{code}` | 200 `{code, url, created_at, expires_at, visit_count, last_visited_at}` |
| GET | `/api/links/{code}/stats` | 200 `{code, days:[{date, count} × 7]}`（UTC、含今日、补零、升序） |
| GET | `/api/links?page=0&size=20` | 200 分页列表（最新在前） |
| GET | `/` | 200 最小前端页 |

错误码：`INVALID_URL` / `INVALID_CODE` / `INVALID_EXPIRY` / `MALFORMED_JSON` 400，`METHOD_NOT_ALLOWED` 405，
`NOT_FOUND` 404，`CODE_ALREADY_EXISTS` 409，`LINK_EXPIRED` 410，`CODE_ALLOCATION_FAILED` 503。

## 5. 关键设计决策（论证见 `docs/design.md`，摘要见 README §3）

| 决策 | 选择 | 一句话理由 |
|---|---|---|
| 短码 | 随机 Base62 7 位 + 唯一约束冲突重试 | 自增码泄露总量且可枚举；hash(url) 与"重复 URL 给新码"冲突 |
| 重定向 | **302** + `no-store` | 301 被浏览器永久缓存 → 统计漏计 |
| 重复 URL | 每次都生成**新**短码 | 渠道独立归因；创建路径无 upsert 竞态；故 `url` 不建索引 |
| 日统计 | 按天聚合表（非事件日志） | 存储有界、读窗口 O(7)；代价是丢明细 |
| 计数 | SQL 原子自增 + `links` 行锁串行化 | 杜绝读-改-写的丢失更新；640 次并发零丢失 |
| 时区 | 全 UTC（`Clock.systemUTC()`） | 日切分不受部署机器时区影响 |

## 6. 测试与验收

```bash
./mvnw verify                                           # 29 个测试，全通过
./mvnw spring-boot:run                                  # 终端 A
BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh   # 终端 B，28 项检查
```

- `docs/TEST_CASES.md` 是验收基线：每条需求 → 用例 → 预期 → 执行它的测试方法。
- 每个集成用例在 `@BeforeEach` 中重置数据库，结果**不依赖执行顺序**。

## 7. 工作约定（踩过的坑，请照做）

1. **提交前必须跑 `./mvnw verify`**。曾经用 `-DskipTests package` 提交，结果漏掉了
   "首页断言"因 UI 中文化而失败 —— 测试没跑就没发现。
2. **打包前先停掉正在运行的 java 进程**：`java -jar target/*.jar` 会占用 jar 文件，
   导致 `spring-boot:repackage` 无法重命名而 BUILD FAILURE（与代码无关）。
   `Get-Process java | Stop-Process -Force`。
3. **文档与 UI 用中文**，接口字段名、错误码、命令保持英文（是契约，不是文案）。
4. **不要动 `.gitattributes`**：`mvnw` 与 `*.sh` 必须保持 LF，CRLF 会让 shebang 失效，
   评审克隆后在 Git Bash 里执行 `./mvnw` 会直接报 `bad interpreter`。
5. **HTML 响应必须带 charset**：`text/html` 无 charset 时按 ISO-8859-1 解码，中文会乱码。
6. 改 V1 迁移脚本会改变 Flyway checksum，只影响已有数据库，本地开发无妨。
7. 已知环境限制：本机 **未安装 Docker**，`docker compose up` 无法在此实测
   （`Dockerfile` / `docker-compose.yml` 已提交，需在有 Docker 的机器上验证）。

## 8. 当前状态与待办

- 本地 Git 仓库：`main` 分支，工作区干净，5 个提交。
- 待办（需仓库所有者操作）：
  1. GitHub 新建 **PRIVATE** 仓库（不要勾选 README / .gitignore / license，避免冲突）
  2. `git remote add origin <url>` && `git push -u origin main`
  3. Settings → Collaborators → 添加 `dolphindbDev`，权限 **Read**
  4. 把仓库 URL 邮件发给 huaxian.tan@dolphindb.com
