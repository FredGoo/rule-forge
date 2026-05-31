# 项目路线图

## 现状

RuleForge 当前已具备的能力：

- **RETE 规则引擎** — 基于 RETE 算法的高性能规则匹配与执行
- **7 种规则类型** — 向导式规则集、脚本式规则集、决策表、脚本决策表、决策树、评分卡、决策流
- **可视化设计器** — React + bpmn-js 的 Web 规则编辑器
- **决策流编排** — 基于 Flowable 8 的 BPMN 2.0 流程引擎
- **陪跑测试** — A/B 对比执行，不影响主流程
- **决策日志** — 完整记录输入、输出、执行明细、各阶段耗时

## 实施顺序

```
监控与告警 ✅ → 上游数据源管理 ✅ → 规则版本与发布管理 ✅ → 下游 Agent 分析 ✅
```

## 路线图总览

| 方向 | 目标 | 优先级 | 状态 |
|------|------|:------:|:----:|
| 监控与告警 | 决策执行全链路可观测 | P1 | ✅ 已完成 |
| 上游数据源管理 | 统一管理外部数据接入 | P0 | ✅ 已完成 |
| 规则版本与发布管理 | 变更审批、灰度发布、回滚、陪跑 | P0 | ✅ 已完成 |
| 下游 Agent 分析 | AI 分析决策结果，优化规则 | P2 | ✅ 已完成 |

---

## 方向一：监控与告警 ✅ 已完成

### 已实现

- **执行耗时 Metrics** — Micrometer + Prometheus，P50/P95/P99 延迟、各阶段耗时分解
- **成功率监控** — 按规则包、决策流统计成功/失败率
- **异常告警** — 失败率超阈值、执行超时主动告警
- **决策趋势看板** — 决策结果分布、通过率趋势

---

## 方向二：上游数据源管理 ✅ 已完成

### 已实现

- **数据源注册中心** — 支持 REST API、JDBC、Advance AI 三种数据源类型，JSON 配置
- **变量映射配置** — 实体级映射（clazz → datasource）+ 字段级映射（规则变量名 → 外部字段名）
- **连接管理** — HikariCP 连接池、超时、缓存策略
- **数据缓存** — 120h TTL，数据库缓存 + 审计日志
- **数据源测试** — 配置阶段测试连通性
- **路由集成** — DatasourceRoutingProvider 替代硬编码，零侵入核心引擎
- **前端界面** — DatasourcePanel（数据源 CRUD + 映射配置 + 字段映射查看）
- **监控与日志** — nd_datasource_log 全链路记录（请求/响应/状态/耗时）

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

## 方向三：下游 Agent 分析 ✅ 已完成

### 设计理念

RuleForge 不内置 LLM，而是提供分析 API + CLI + Skills，让外部 Agent（Claude Code、Cursor 等）调用。CLI 是最通用的 Agent 接口，不会过时。

### 关键特性

- **决策日志聚合分析** — 按时间窗口统计调用量、成功率、拒绝率、延迟趋势 ✅
- **偏差检测** — 7 天日均值基线 + sigma 阈值异常检测 ✅
- **规则覆盖率分析** — 热/冷/死规则分类、触发频率分布 ✅
- **优化建议生成** — 基于 LLM（推迟）
- **决策归因** — 单条决策归因（推迟）

### 已实现

- **DecisionAnalysisMapper** — @Select 聚合 SQL：时间序列、包汇总、拒绝码分布、偏差基线/当前窗口
- **RuleCoverageMapper** — 规则触发频率排名、全量曾触发规则名
- **AnalysisServiceImpl** — ECharts 格式转换、热/冷/死规则分类、sigma 阈值偏差检测
- **AnalysisController** — REST API 7 个端点（/analysis/*）
- **ExportController** — 规则内容导出 4 个端点（/export/*），输出原始 XML/JSON 供 Agent 理解
- **前端分析仪表盘** — 三 Tab（决策趋势、规则覆盖、偏差检测）+ ECharts 图表
- **ruleforge CLI** — Node.js 命令行工具，analysis + export 两大命令组
- **Claude Code Skills** — 6 个 skill（analyze-package, analyze-decision, check-coverage, detect-anomaly, review-rules, simulate-impact）

### 模块结构

```
Agent 分析模块
├── DecisionAnalysisMapper   聚合 SQL（时间序列、包汇总、拒绝码、偏差基线）
├── RuleCoverageMapper       规则触发频率、覆盖率
├── AnalysisServiceImpl      分析逻辑（ECharts 转换、规则分类、偏差检测）
├── AnalysisController       REST API（/analysis/*）
├── ExportController         规则内容导出（/export/*）
├── Analysis Dashboard       前端分析仪表盘（React + ECharts）
├── ruleforge CLI            命令行工具（Agent 接口）
└── .claude/skills/          Claude Code Skills（6 个）
```

---

## 方向四：规则版本与发布管理 ✅ 已完成

### 现状

规则文件有版本历史（`fileVersions` 接口），但缺少审批流程、环境隔离和灰度发布能力。规则保存即生效，没有变更门禁。

### 目标

建立完整的规则变更生命周期管理：编辑 → 审批 → 发布 → 监控 → 回滚。

### 关键特性

- **变更审批工作流** — 规则变更需经过审批才能发布到生产环境 ✅
- **环境隔离** — dev / staging / prod 环境独立，规则按环境发布 ✅
- **灰度发布** — 新规则先对部分流量生效，验证无误后全量发布 ✅
- **版本 Diff** — 发布前可视化对比新旧版本差异 ✅
- **一键回滚** — 出问题时秒级回退到上一版本 ✅
- **应用层灰度路由** — executor-app 内部根据请求特征路由到不同规则版本 ✅
- **陪跑流量重放** — 异步执行影子规则包，自动对比主/陪跑结果差异 ✅

### 已实现

- **审批任务表** — `gr_approval_task` 支持内部审批流程，auto/manual 两种模式
- **ApprovalController** — REST 端点：listPending / approve / reject / listByProject
- **DeploymentController** — REST 端点：deploy / current / history / environments / promote / rollback / registerNode / heartbeat
- **ExternalProcessServiceImpl** — 真审批逻辑替代硬编码 stub，配置 `ruleforge.approval.mode`
- **结构化 Diff API** — `getPackageDiffStructured` / `getFileDiffStructured` 返回 `List<FileDiff>` JSON
- **ReleasePanel 前端** — 三 Tab 面板（环境管理 / 审批流程 / 部署历史），替换 PlaceholderPanel
- **DiffViewer 组件** — 基于 diff2html 的 side-by-side 可视化 diff
- **一键回滚** — 部署历史表格中的回滚按钮，自动通知 executor

### 模块结构

```
版本与发布模块
├── ApprovalTaskEntity     审批任务实体
├── ApprovalRepository     审批任务数据访问
├── ApprovalController     审批 REST 端点
├── DeploymentController   部署管理 REST 端点
├── GrayStrategyService    灰度策略服务（WHITELIST/PERCENT_USER/PERCENT_RANDOM）
├── GrayVersionContext     ThreadLocal 灰度版本透传
├── ShadowExecutionService 陪跑异步执行服务
├── ShadowComparisonService 陪跑结果自动对比（4维度 × 4级严重度）
├── ReleasePanel           前端版本发布面板（6 Tab）
└── DiffViewer             前端可视化 diff 组件
```
