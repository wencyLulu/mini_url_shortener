# Mini URL Shortener — 任务清单

项目根：`url-shortener`（Maven 工程，可直接作为提交仓库根目录）

> 状态说明：**全部 8 项任务已完成**。`实际结果` 一栏记录了与原计划不同的地方。

## T1 工程骨架 ✅
- `pom.xml`：parent `spring-boot-starter-parent:3.2.1`，Java 17，依赖 web / data-jpa / cache /
  caffeine / h2(runtime) / flyway-core / spring-boot-starter-test；`spring-boot-maven-plugin`。
- Maven Wrapper（`mvn -N wrapper:wrapper -Dmaven=3.9.9`），保证 `./mvnw spring-boot:run` 一条命令启动。
- `application.yml`：H2 文件库 `jdbc:h2:file:./data/urlshortener;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=20000`，
  Flyway 开启、`ddl-auto=none`，Jackson 关闭时间戳输出 + `SNAKE_CASE` 命名，`app.base-url`。
- 验收：`mvn -q -DskipTests package` 通过。
- **实际结果**：额外增加 `.gitattributes` 固定换行符（否则 Windows 检出会把 `mvnw` 转成 CRLF，
  shebang 失效导致一键启动失败）；移除未使用的 `spring-boot-starter-validation`。

## T2 数据层 ✅
- `V1__create_schema.sql`：`links` + `link_daily_visit` + 三处索引/约束（含 `ON DELETE CASCADE`）。
- 实体 `Link` / `LinkDailyVisit`；`LinkRepository`（`findByCode`、分页查询）、
  `LinkDailyVisitRepository`（`@Modifying` 原子自增、7 日区间查询）。
- 验收：测试环境启动能建表。
- **实际结果**：未使用 `src/test/resources/application-test.yml`，改为在每个集成测试类上用
  `@TestPropertySource` 指定独立的 H2 内存库，隔离更明确。

## T3 校验与错误 ✅
- `UrlValidator`：非空 / http|https / ≤2048；`CodePolicy`：自定义码 `[A-Za-z0-9]{3,32}` + 保留字黑名单。
- 异常族 + `GlobalExceptionHandler` + `ErrorResponse`。
- 测试：非法 URL → 400 `INVALID_URL`；未知码 → 404 `NOT_FOUND`；坏 JSON → 400 `MALFORMED_JSON`；
  空请求体 → 400；不支持的方法 → 405。

## T4 创建短链 ✅
- `CodeGenerator`（Base62 / 7 位 / SecureRandom）+ 冲突重试（`LinkStore` 独立事务，上限 10）。
- `LinkService.create` → 201 + `LinkResponse`（code/short_url/url/created_at/expires_at）。
- 测试：创建成功 201；重复 URL 得到不同码；自定义码冲突 409。

## T5 重定向 + 计数 ✅
- `RedirectService.resolve` → `302` + `Location`，未命中 404，过期 410。
- `VisitCounter`：总量与日计数在**同一个短事务**中原子自增，先更新 `links` 取行锁；
  `VisitRecorder` 负责冲突时的重放。
- 测试：302 + Location 正确；并发 32×20 重定向后 `visit_count` 精确等于 640。
- **实际结果**：最初把两个计数器拆在两个 `REQUIRES_NEW` 事务里，日志中出现预期内的唯一冲突；
  改为单事务 + 行锁串行化后，冲突在正常路径下不再发生。

## T6 详情与统计 ✅
- `GET /api/links/{code}` → `LinkDetailsResponse`。
- `GET /api/links/{code}/stats` → 近 7 日（UTC，含今日，缺日补 0，升序）。
- 测试：直接插日行，断言窗口边界（今天-6 在窗口内、今天-8 不在）与补零。

## T7 加分项 ✅
- 自定义短码（T4 已含）、`expires_at` 过期 410、Caffeine `@Cacheable` + `@CacheEvict`、
  `Dockerfile` + `docker-compose.yml`、`static/index.html` + `GET /api/links` 分页列表。
- **实际结果**：首页曾被 `/{code}` 通配映射吞掉（404），新增 `IndexController` 显式映射 `/`；
  页面改为中文后暴露 `text/html` 未声明 charset 导致的乱码，已改为 `charset=UTF-8`。

## T8 README + 评审 + 用例固化 ✅
- README（中文）：启动命令、curl 示例、5 项设计决策、扩展思路。
- `docs/TEST_CASES.md`：39 条编号用例（TC-001 … TC-039）的验收基线。
- `scripts/smoke-test.sh`：对运行中实例的 28 项端到端检查，实测全通过。
- 全量 `./mvnw verify` 通过：**29 个测试**。

## 执行说明
- 每个 T 遵循 Red-Green-Refactor：先落测试，再写实现，再跑测试。
- 本工程规模小且模块耦合紧，由主 agent 顺序实现（不派 subagent 并行），避免接口不一致。

## 未做（明确超出范围）
- 认证鉴权、多租户、删除/编辑接口、水平扩展（思路见 README §6）。
- `docker compose up --build` 本机未实测（本机无 Docker），`Dockerfile` / `docker-compose.yml` 已随代码提交。
