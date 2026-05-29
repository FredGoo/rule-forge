# 架构概览

## 模块结构

```
ruleforge-parent        Maven parent POM, Spring Boot BOM, Java 17
ruleforge-core          RETE 规则引擎（解析、执行、知识库）
ruleforge-console       Web 控制台业务逻辑（controllers, services, DB）
ruleforge-executor      规则执行引擎（测试端点、知识包加载）
ruleforge-console-app   可部署 Spring Boot 应用 — 编辑器（端口 8081）
ruleforge-executor-app  可部署 Spring Boot 应用 — 执行器（端口 8082）
frontend                React 可视化规则设计器
```

依赖链：

```
core ← console ← console-app
core ← executor ← executor-app
```

## 技术栈

- Java 17, Spring Boot 4.0.6, Spring Framework 7
- MyBatis-Plus 3.5.9, MySQL, Flyway
- ANTLR4, Jackson, fastjson2, HikariCP
- Flowable 8 BPM 引擎（决策流执行）
- 前端：React, bpmn-js（决策流设计器）

## 决策执行流程

RuleForge 基于 RETE 算法，上游系统调用它做贷款审批、提现风控等决策。

```
上游系统 → POST /api/loan/evaluate → RuleForge
                                        │
                            ┌───────────┼───────────┐
                            │ 1.查变量定义(DB)       │
                            │ 2.加载知识包(repo)     │
                            │ 3.创建RETE会话         │
                            │ 4.插入实体(用户数据)    │
                            │ 5.执行决策流           │
                            │ 6.收集OutputModel      │
                            │ 7.写DecisionLog(DB)    │
                            │ 8.触发陪跑(可选)       │
                            └───────────────────────┘
```

### 1. 准备变量定义

从 DB 表 `rule_variable_def` 加载所有变量定义，按 `clazz` 分组。

### 2. 加载知识包

从 RuleForge 仓库加载指定 `rulePackagePath` 的知识包（含规则集、决策表、决策树、评分卡等）。

### 3. 创建 RETE 会话

基于知识包创建 `KnowledgeSession`，RETE 网络准备就绪。

### 4. 插入实体

- **OutputModel**: 收集决策结果（decision、creditLimit、product 等）
- **LazyGeneralEntity**: 按 clazz 分组，懒加载用户数据。只在实际引用时才查询数据库。

### 5. 执行决策流

`session.startProcess(flowId)`，支持 6 种规则类型：向导式规则集、脚本式规则集、决策表、交叉决策表(PRO)、决策树、评分卡。

### 6. 收集结果

| 字段 | 类型 | 说明 |
|------|------|------|
| ruleResult | String | 决策: APPROVE / MANUAL_REVIEW / REQUIRES_MORE_INFO / REJECT |
| creditLimit | BigDecimal | 授信额度 |
| product | String | 产品标签 (如 P001) |
| lockDays | Integer | 锁定期天数 |
| ifManualReview | Boolean | 是否需人工审核 |
| creditLimit_validDay | Integer | 额度有效期(天) |
| rule_score | Integer | 规则评分 |

### 7. 决策日志 (decision_log 表)

| 字段 | 说明 |
|------|------|
| userId, orderNo, flowId | 请求标识 |
| rulePackagePath | 使用的规则包 |
| status | SUCCESS / FAILED / PENDING |
| inputParams | 输入参数 (JSON) |
| resultData | 输出结果 (JSON) |
| entityDataMap | 实体数据 (JSON) |
| execMessageItems | RETE 执行明细 (JSON) |
| 各阶段耗时(ms) | 查变量/加载知识/创建会话/插入实体/执行流程 |
| errorMessage, stackTrace | 失败时记录 |

### 8. 陪跑 (Shadow Execution)

A/B 测试：主规则包执行的同时，异步触发陪跑规则包。不影响主流程。

## 模块详情

### ruleforge-core (`com.ruleforge.*`)

纯引擎，无 Spring Boot 依赖：

- `model/` — 规则模型（rule, table, tree, scorecard, library）
- `model.rete/` — RETE 算法实现
- `runtime/` — 知识会话、执行、缓存
- `parse/` — XML/DSL 规则解析器（ANTLR4）
- `controller/` — KnowledgePackageReceiverServlet

### ruleforge-console (`com.ruleforge.console.*`)

Web 编辑器后端：

- `controller/` — REST 控制器（frame, common, package）
- `flow/` — Flowable 8 集成（delegates, controller, converter）
- `service/` — repository, permission, test 服务
- `storage/` — 项目存储（数据库支持）
- `mapper/` — MyBatis-Plus mappers
- `repository/` — 模型类（RepositoryFile, VersionFile, ResourcePackage 等）
- `servlet/` — 工具类（RequestContext, ErrorInfo, ScriptType）
- `config/` — MybatisPlusConfig, FlywayConfig

### ruleforge-executor (`com.ruleforge.executor.*`)

规则执行：

- `controller/TestController` — `/test/do`, `/test/knowledge`
- `service/` — RuleForgeService, KnowledgePackageServiceImpl
- `service/impl/ExecResourceProvider` — 通过 HTTP 从 console 获取资源

### ruleforge-console-app (`com.ruleforge.console.app.*`)

可部署编辑器，包含数据源配置、环境提供者和业务特定代码（贷款决策、陪跑、决策日志）。

### ruleforge-executor-app (`com.ruleforge.executor.app.*`)

可部署执行器，包含 RestTemplate 配置用于与 console 通信。

## 配置

- Console-app: 端口 8081，双数据源（app + ruleforge），`ruleforge.exec.url` → executor
- Executor-app: 端口 8082，`ruleforge.console.url` → console
