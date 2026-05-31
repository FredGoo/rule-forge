# 项目路线图

## 现状

RuleForge 当前已具备的能力：

- **RETE 规则引擎** — 基于 RETE 算法的高性能规则匹配与执行
- **7 种规则类型** — 向导式规则集、脚本式规则集、决策表、脚本决策表、决策树、评分卡、决策流
- **可视化设计器** — React + bpmn-js 的 Web 规则编辑器
- **决策流编排** — 基于 Flowable 8 的 BPMN 2.0 流程引擎
- **陪跑测试** — A/B 对比执行，不影响主流程
- **决策日志** — 完整记录输入、输出、执行明细、各阶段耗时

## 版本管理

采用语义化版本 5.x 系列，Flyway 迁移版本与 POM 版本同步：

```
5.0.0 = Phase 1-4（监控告警、数据源、版本发布、Agent 分析）
5.1.0 = Phase 5 规则仿真
5.2.0 = Phase 6 前端 UI 现代化
5.3.0 = Phase 7 AgentScope 集成
5.4.0 = Phase 8 ClickHouse 分析
5.5.0 = Phase 9 数据源批量测试
5.6.0 = Phase 10 文档与 Demo
5.7.0 = Phase 11 PMML/PKL 模型
5.8.0 = Phase 12 Rust 高性能引擎（远期）
```

Flyway 版本规则：Phase 5 → `V5.1.0__xxx.sql`，Phase 6 → `V5.2.0__xxx.sql`，依此类推。

## 实施顺序

```
Phase 1-4 ✅ 已完成 → Phase 5-12 📋 规划中
```

```
Phase 5  规则仿真         P0  ─┐
Phase 6  前端 UI 现代化   P0  ─┤── 先做
Phase 7  AgentScope       P1  ─┤
Phase 8  ClickHouse       P1  ─┤── 其次
Phase 9  数据源批量测试   P1  ─┤
Phase 10 文档 Demo        P2  ─┤── 锦上添花
Phase 11 PMML/PKL         P2  ─┤
Phase 12 Rust 引擎        P3  ─┘── 远期
```

## 路线图总览

| 方向 | 目标 | 版本 | 优先级 | 状态 |
|------|------|:----:|:------:|:----:|
| 监控与告警 | 决策执行全链路可观测 | 5.0.0 | P1 | ✅ 已完成 |
| 上游数据源管理 | 统一管理外部数据接入 | 5.0.0 | P0 | ✅ 已完成 |
| 规则版本与发布管理 | 变更审批、灰度发布、回滚 | 5.0.0 | P0 | ✅ 已完成 |
| 下游 Agent 分析 | AI 分析决策结果，优化规则 | 5.0.0 | P2 | ✅ 已完成 |
| 规则仿真 | 批量回放历史流量，预知变更影响 | 5.1.0 | P0 | 📋 规划中 |
| 前端 UI 现代化 | Ant Design 5 + ProLayout | 5.2.0 | P0 | 📋 规划中 |
| AgentScope 集成 | Web 内置 AI 对话分析 | 5.3.0 | P1 | 📋 规划中 |
| ClickHouse 分析 | 高性能分析数据库 | 5.4.0 | P1 | 📋 规划中 |
| 数据源批量测试 | CSV/JSON 批量导入测试 | 5.5.0 | P1 | 📋 规划中 |
| 文档与 Demo | GitHub Pages + VitePress | 5.6.0 | P2 | 📋 规划中 |
| PMML/PKL 模型 | Python 模型导入执行 | 5.7.0 | P2 | 📋 规划中 |
| Rust 执行引擎 | RETE 高性能重写 | 5.8.0 | P3 | 📋 远期规划 |

---

## Phase 1: 监控与告警 ✅ 已完成 (5.0.0)

### 已实现

- **执行耗时 Metrics** — Micrometer + Prometheus，P50/P95/P99 延迟、各阶段耗时分解
- **成功率监控** — 按规则包、决策流统计成功/失败率
- **异常告警** — 失败率超阈值、执行超时主动告警
- **决策趋势看板** — 决策结果分布、通过率趋势

---

## Phase 2: 上游数据源管理 ✅ 已完成 (5.0.0)

### 已实现

- **数据源注册中心** — REST API / JDBC / Advance AI 三种连接器，JSON 配置
- **变量映射配置** — 实体级映射 + 字段级映射（规则变量名 → 外部字段名）
- **连接管理** — HikariCP 连接池、超时、缓存策略
- **数据缓存** — 120h TTL，数据库缓存 + 审计日志
- **数据源测试** — 配置阶段测试连通性
- **路由集成** — DatasourceRoutingProvider 替代硬编码，零侵入核心引擎
- **前端界面** — DatasourcePanel（数据源 CRUD + 映射配置 + 字段映射查看）
- **监控与日志** — nd_datasource_log 全链路记录

### 模块结构

```
数据源管理模块
├── DatasourceRegistry        数据源注册与元数据（nd_datasource）
├── DataSourceConnectors      各类型连接器（REST/JDBC/AdvanceAI）
├── VariableMapping           数据字段 → 规则变量映射（entity_mapping + field_mapping）
├── DatasourceCache           数据缓存层（DB 缓存 + TTL）
└── DatasourceRoutingProvider  零侵入路由集成
```

---

## Phase 3: 规则版本与发布管理 ✅ 已完成 (5.0.0)

### 已实现

- **变更审批工作流** — auto/manual 两种模式，gr_approval_task 表
- **环境隔离** — dev / staging / prod
- **灰度发布** — WHITELIST / PERCENT_USER / PERCENT_RANDOM 三种策略
- **结构化 Diff API** — side-by-side 可视化 diff
- **一键回滚** — 部署历史回滚按钮
- **陪跑流量重放** — ShadowExecutionService + ShadowComparisonService（4 维度 × 4 级严重度）
- **Flyway** — V3.1.0 ~ V3.14.0

### 模块结构

```
版本与发布模块
├── ApprovalTaskEntity     审批任务实体
├── ApprovalController     审批 REST 端点
├── DeploymentController   部署管理 REST 端点
├── GrayStrategyService    灰度策略服务
├── ShadowExecutionService 陪跑异步执行服务
├── ShadowComparisonService 陪跑结果自动对比
├── ReleasePanel           前端版本发布面板
└── DiffViewer             前端可视化 diff 组件
```

---

## Phase 4: 下游 Agent 分析 ✅ 已完成 (5.0.0)

### 设计理念

RuleForge 不内置 LLM，提供分析 API + CLI + Skills，让外部 Agent（Claude Code、Cursor 等）调用。CLI 是最通用的 Agent 接口。

### 已实现

- **DecisionAnalysisMapper** — 聚合 SQL：时间序列、包汇总、拒绝码分布、偏差基线
- **RuleCoverageMapper** — 规则触发频率排名、全量曾触发规则名
- **AnalysisServiceImpl** — ECharts 格式转换、热/冷/死规则分类、sigma 阈值偏差检测
- **AnalysisController** — REST API 7 个端点（/analysis/*）
- **ExportController** — 规则内容导出 4 个端点（/export/*）
- **前端分析仪表盘** — 三 Tab（决策趋势、规则覆盖、偏差检测）+ ECharts
- **ruleforge CLI** — Node.js 命令行工具（analysis + export 命令组）
- **Claude Code Skills** — 6 个 skill

### 模块结构

```
Agent 分析模块
├── DecisionAnalysisMapper   聚合 SQL
├── RuleCoverageMapper       规则触发频率、覆盖率
├── AnalysisServiceImpl      分析逻辑
├── AnalysisController       REST API（/analysis/*）
├── ExportController         规则内容导出（/export/*）
├── Analysis Dashboard       前端分析仪表盘
├── ruleforge CLI            命令行工具
└── .claude/skills/          Claude Code Skills（6 个）
```

---

## Phase 5: 规则仿真（主动模拟） 📋 规划中 (5.1.0)

### 问题

规则变更前无法预知影响范围，现有陪跑是被动跑影子，缺少主动模拟工具。

### 方案

在现有 `ShadowExecutionService` + `TestController` 基础上，新增批量仿真 API 和前端界面。

### 关键特性

- **批量历史回放** — 从 DB 加载历史流量 → 构建批量请求 → 发到 executor → 对比结果
- **仿真结果对比** — 复用 ShadowComparisonService 的 4 维度对比
- **前端仿真面板** — 选规则包 + 时间范围 → 仿真 → diff 报告
- **CLI 集成** — `ruleforge simulation run --package loan-rules --from 2026-05-01`

### 新增模块

```
仿真模块
├── SimulationController        批量仿真 REST API
├── SimulationServiceImpl       加载历史流量 → 重放 → 对比
├── SimulationPanel             前端仿真面板
├── nd_simulation_result        仿真结果存储表（Flyway V5.1.0）
└── CLI simulation 命令
```

### 实施步骤

1. 后端仿真 API（批量加载历史决策 → 重放到指定规则包版本）
2. 仿真结果对比（复用 ShadowComparisonService）
3. 前端仿真面板（触发 → 进度 → 结果 diff）
4. CLI 集成

---

## Phase 6: 前端 UI 现代化 📋 规划中 (5.2.0)

### 问题

Bootstrap 3.4.1 已过时，UI 影响产品形象和用户体验。

### 方案

采用 **Ant Design 5 + ProLayout**（React 18 生态成熟，中文文档完善，管理后台标准方案）。

### 关键变更

- Bootstrap 3.4.1 → Ant Design 5.x
- jQuery 依赖 → React 状态管理
- ProLayout 管理后台框架
- 保留 bpmn-js、ECharts、CodeMirror
- 可选：Webpack → Vite

### 实施策略（分批）

1. 搭 Ant Design ProLayout 框架，替换整体布局
2. 逐页面迁移（分析仪表盘 → 发布管理 → 规则设计器）
3. 最后移除 Bootstrap 依赖

---

## Phase 7: 内置 Agent（AgentScope 集成） 📋 规划中 (5.3.0)

### 问题

风控人员不会用 Claude Code / CLI，需要 Web 内置的 AI 对话分析。

### 方案

集成 **AgentScope Java**（阿里开源，Spring Boot Starter，兼容 OpenAI/Anthropic API）。

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-spring-boot-starter</artifactId>
    <version>1.0.5</version>
</dependency>
```

### 新增模块

```
Agent 模块（console-app 内）
├── AgentConfig          AgentScope + LLM API 配置
├── AgentController      /agent/chat REST + SSE 流式
├── RuleAnalysisAgent    规则分析 Agent（调用分析 API）
├── RuleSuggestAgent     规则优化建议 Agent
└── 前端 AgentPanel      对话界面
```

### 关键设计

- 只需配置 OpenAI 或 Anthropic 格式的 API 地址和 Key
- Agent 的 Tool 就是现有的分析/导出 REST API
- 支持流式输出（SSE）
- 不绑定特定模型 — 用户自己配 API

---

## Phase 8: 高性能分析数据库（ClickHouse） 📋 规划中 (5.4.0)

### 问题

MySQL 聚合查询在大数据量下性能不足，决策日志每天可能几十万条。

### 方案

引入 **ClickHouse** 作为分析存储，MySQL 保持事务存储。

### 架构（双写 + 查询路由）

```
决策日志写入
    ├── MySQL（事务，实时查询）
    └── ClickHouse（异步批量写入，分析查询）

分析 API 查询路由
    ├── 小数据量 → MySQL（现有 Mapper）
    └── 大数据量/历史分析 → ClickHouse（新增 Mapper）
```

### 新增模块

```
分析存储模块
├── AnalyticsDataSourceConfig       ClickHouse 数据源配置
├── DecisionLogAnalyticsMapper      ClickHouse 聚合查询
├── DecisionLogClickHouseWriter     异步批量写入
└── Flyway V5.4.0__clickhouse_sync.sql
```

---

## Phase 9: 数据源批量测试 📋 规划中 (5.5.0)

### 问题

数据源配置后只能单条测试，无法验证批量调用性能和正确性。

### 方案

扩展数据源测试功能，支持 CSV/JSON 批量导入 → 批量调用 → 结果对比。

### 新增

- `DatasourceBatchTestController` — 批量测试 REST API
- `DatasourceBatchTestServiceImpl` — 读取测试数据 → 批量调用 → 记录结果
- 前端：`DatasourceBatchTestPanel` — 上传 CSV → 配置映射 → 运行 → 查看结果
- CLI：`ruleforge datasource batch-test --source myapi --input test_data.csv`

---

## Phase 10: 文档与 Demo 网站 📋 规划中 (5.6.0)

### 问题

缺少公开文档和 Demo，不利于推广和新人上手。

### 方案

**GitHub Pages + VitePress**

```
docs-site/
├── .vitepress/config.js
├── guide/           使用指南
├── api/             API 参考
├── tutorial/        教程（小微信贷决策流 Demo）
└── demo/            在线 Demo 链接
```

- GitHub Actions：推送到 main → 自动构建部署到 GitHub Pages
- Demo：Docker Compose 一键启动

---

## Phase 11: PMML/PKL 模型支持 📋 规划中 (5.7.0)

### 问题

风控模型用 Python 训练，导出 PMML 或 PKL，规则引擎无法直接使用。

### 方案

- **PMML** — `org.jpmml:pmml-evaluator` 加载 PMML 文件 → 输入变量 → 输出预测结果
- **PKL** — 独立 Python 微服务（Flask + pickle.load → REST API），通过 REST 调用
- 集成为新的规则动作类型 — "模型预测"动作

---

## Phase 12: Rust 高性能执行引擎 📋 远期规划 (5.8.0)

### 问题

RETE 算法在极端高并发下 Java GC 可能影响延迟。

### 方案

用 Rust 重写核心 RETE 匹配引擎，通过 JNI 调用。

### 评估

- 当前 KnowledgeSessionImpl 约 20K 行代码，重写工作量大
- Java 17 + GraalVM Native Image 也是一种优化路径
- **建议暂缓**，先做性能压测，确认瓶颈确实在引擎而非 IO/网络
