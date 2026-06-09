# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Maven-based Java 17 project, Spring Boot 4.0.6. Run from `server/`:

```bash
mvn compile                                          # Compile all modules
mvn compile -pl lib/ruleforge-core -am               # Compile single module with deps
mvn clean package -DskipTests                        # Package without tests
```

Frontend is in `console-ui/`, check its package.json for npm commands.

Docs site is in `docs-site/` (VitePress):

```bash
cd docs-site
npm install          # Install VitePress
npm run dev          # Dev server (localhost:5173)
npm run build        # Production build
```

Docker Compose full stack:

```bash
docker compose up -d              # Start all 5 services
docker compose logs -f console-app # View logs
```

## Project Architecture

```
server/
├── ruleforge-parent/        Maven parent POM, Spring Boot BOM, Java 17
├── lib/                     共享库模块(被 app 依赖,无 Spring Boot 启动类)
│   ├── ruleforge-core       RETE rule engine (parsing, execution, knowledge base)
│   ├── ruleforge-decision   决策流引擎 + 数据源注册 (V5.18 self-built FlowEngine)
│   └── ruleforge-datasource 数据源抽象基类 + 编译/加载基础设施 (V5.23+)
└── app/                     可部署 Spring Boot 应用(平级,互不依赖)
    ├── ruleforge-console-app   Editor (port 8081)
    └── ruleforge-executor-app  Executor (port 8082)
frontend                React-based visual rule designer
```

Dependency chain:
```
core ← datasource ← decision ← console-app
                          ←  executor-app
```

## Module Details

### lib/ruleforge-core (`com.ruleforge.*`)
Pure engine, no Spring Boot dependency:
- `model/` — rule models (rule, table, tree, scorecard, library)
- `model.rete/` — RETE algorithm implementation
- `runtime/` — knowledge session, execution, caching
- `parse/` — XML/DSL rule parsers (ANTLR4)
- `controller/` — KnowledgePackageReceiverServlet

### lib/ruleforge-decision (`com.ruleforge.decision.*`)
Self-built decision flow engine (V5.18, replaced Flowable 8):
- `flow/` — FlowEngine + 9 NodeExecutors (Rule / Action / Script / Gateway / UserTask / ...)
- `flow.persistence/` — nd_decision_flow_state CRUD + recovery job
- `flow.executor/` — ScriptNodeExecutor (JSR-223, optional Groovy)

### lib/ruleforge-datasource (`com.ruleforge.datasource.*`) — V5.23+ NEW
Data source abstraction, shared by console-app and executor-app:
- `BaseApiDataSource` — LLM-generated subclasses extend this (calls third-party HTTP APIs)
- `DataSourceRegistry` — Spring bean registry; decision engine calls `fetch(name, vars)`
- `JavaSourceCompiler` — pure function `compile(javaCode) -> byte[]` (forkJavac, no AWS/S3/SQS)
- `ClassLoaderPool` — load .class bytes into isolated URLClassLoader
- `DataSourceAuditLog` — interface; each app implements to write its own audit table
- **No DB deps** — lib never connects to MySQL/Postgres, persistence is each app's responsibility

### app/ruleforge-console-app (`com.ruleforge.console.app.*`)
Deployable editor: BA UI backend, agent tools, draft flow, git storage, dual datasource (app_db + ruleforge_db).

### app/ruleforge-executor-app (`com.ruleforge.executor.app.*`)
Deployable executor: loads compiled data sources from git on startup, runs decision flows.

## Key Technologies

- Java 17, Spring Boot 4.0.6, Spring Framework 7
- MyBatis-Plus 3.5.9, MySQL, Flyway
- ANTLR4, Jackson, fastjson2, HikariCP
- Flowable 8 BPM engine for decision flow execution
- Frontend: TypeScript, React, Vite 8, Ant Design 5, bpmn-js for flow designer
- Frontend HTTP: centralized `src/api/client.ts` (formPost, jsonPost, jsonPut, httpGet, httpDelete, save, saveNewVersion)
- Frontend tests: Vitest unit tests, Playwright E2E tests

## Development Principles

### BDD/TDD

- Follow Behavior Driven Development practices for test writing:
  1. Write test files with Gherkin-style behavior annotations (Given/When/Then) in @DisplayName/@Nested
  2. Write test code directly after annotations — no need to confirm with user before writing test code
- Follow Test Driven Development: never write business/implementation code before writing tests

### Versioning Convention (V5.16 起)

**格式: `Major.Feature.Fix` — 迁移文件 `V{MAJOR}.{FEATURE}.{FIX}__*.sql`,分支 `feature/{MAJOR}.{FEATURE}-{slug}`,CHANGELOG 标签 `v{MAJOR}.{FEATURE}.{FIX}`**

| 位 | 语义 | 递增规则 |
|---|---|---|
| **Major** | 里程碑(milestone) | 大版本升级(架构 / 数据模型 / 部署协议级别)才动;**不**要求每次 release 都加 |
| **Feature** | 任意特性(feature) | **不**要求严格 +1,可以跳号(3.1.3 → 3.1.4 → 3.5.3,跳过未做的小版本) |
| **Fix** | 修小 bug / 增量迁移 | 同 Feature 内的后续微调(补字段、bugfix) |

跟标准 SemVer 的区别:
- Feature 位不严格递增 — 项目里跳号是历史事实,延续这个习惯
- Major 不绑定"破坏性" — V3 → V5 是 2024-2025 里程碑(gr_* 换 rf_*,MyBatis-Plus 升级),不是 API breaking
- Flyway 跟 Maven 版本号不强绑定 — Maven version 是 1.0.0 / 4.0.6(Spring Boot),Flyway migration 用项目自己的节奏

**硬规则:已发布在 dev / 生产 DB 上的版本号不允许改写**(改了 Flyway checksum 校验失败);要"修"已发布的版本只能再发一个 `V{同号}.{+1}__*.sql`。

### Module Boundaries — 禁止"借实体"

**核心规则:`app/ruleforge-console-app` 和 `app/ruleforge-executor-app` 是平行的可部署 Spring Boot app,互不依赖。** 共享的只有 `lib/ruleforge-core` / `lib/ruleforge-decision` / `lib/ruleforge-datasource` 库模块。

**禁止的反模式**:
- ❌ console-app 里 `import com.ruleforge.decision.entity.*`(这些 entity 在 lib/ruleforge-decision,executor-app 是其下游消费者)
- ❌ console-app 里 `import com.ruleforge.decision.mapper.clickhouse.*`(同理)
- ❌ 任何"在 A app 里 import B app 的类"的操作 — 看起来能编(因为 IDE 把整 monorepo 都 index 了),**实际上 pom.xml 没声明依赖,`mvn -pl <app> package` 一定失败**

**判断当前表/Entity 该归谁**:
| 表/Entity | 所属模块 | 注入的 DataSource |
|---|---|---|
| `rf_*` (V5.15 起的权限/用户/审计) | `app/ruleforge-console-app` 专属 | `ruleforgeDataSource` (`ruleforge_db`) |
| `nd_*` (V5.1~V5.13 的批测/agent/监控/决策日志) | `app/ruleforge-console-app` 专属 | `appDataSource` (`app_db`) |
| `act_*` / `flw_*` (Flowable 引擎) | `app/ruleforge-console-app` + executor-app 共用 | `flowable` (`flowable_db`) |
| `nd_decision_flow_state` (V5.19+) | `lib/ruleforge-decision` 共用 | 各自 app 注入(`appDataSource` 或独立 ds) |

**"我的表/Entity 在哪个 module / DataSource 怎么选" 速查**:
- `com.ruleforge.console.app.entity.*`  → `app/ruleforge-console-app` / 看 entity 注释指明 DataSource
- `com.ruleforge.decision.entity.*`    → `lib/ruleforge-decision` / 各 app 注入
- `com.ruleforge.datasource.*`         → `lib/ruleforge-datasource` / **不连 DB**(持久化由 app 决定)

**一次性批处理工具(回填 / 迁移 / dual-write 补偿)的实现准则**:
- **优先 raw JDBC + `PreparedStatement`**,不要套 MyBatis-Plus `@Mapper` + `Entity`
- 抽象成本 ≥ 实际收益:这类工具跑一次就完事,以后改 schema 改 1-2 个文件比维护一套 mapper 注解省事
- 不要借其他 app 的 entity(强制规则同 Module Boundaries)— 用本地 `record` DTO 镜像行结构
- `BATCH_SIZE` + lastId 分页是 MySQL 大表标准打法
- 单行失败 catch + `log.warn` 跳过,不要让一条脏数据中断整个 batch
- 写测试锁边界:构造器签名 = 期望的 DataSource 类型 + 字段不出现跨模块 import — 防止以后 PR 又把跨模块引用塞回来

**反面教材**:`ClickHouseBackfillRunner` (Phase 8) 同时违反上面三条 — console 借 executor 的 entity、注入错的 DataSource、用 mapper 而不是 JDBC。结果 main 合并后 `mvn package` 直接失败,生产 jar 一直打不出来。修法见 CHANGELOG `fix/phase8-clickhouse-backfill-self-contained`。

## Rule Types

向导式规则集, 脚本式规则集 (UL), 决策表, 脚本决策表, 决策树, 评分卡, 决策流

## Configuration

- Console-app: port 8081, dual datasource (app + ruleforge), `ruleforge.exec.url` → executor
- Executor-app: port 8082, `ruleforge.console.url` → console
