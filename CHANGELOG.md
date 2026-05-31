# Changelog

All notable changes to RuleForge will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [5.0.0] - 2026-05-31

### Added

**Phase 1: 监控与告警**
- Micrometer + Prometheus 指标采集：P50/P95/P99 延迟、各阶段耗时分解
- 告警引擎：失败率超阈值、执行超时主动告警
- 决策趋势看板：决策结果分布、通过率趋势
- 批量 INSERT 优化决策日志写入性能

**Phase 2: 上游数据源管理**
- 数据源注册中心：REST API / JDBC / Advance AI 三种连接器
- 变量映射配置：实体级映射 + 字段级映射（JSON 配置）
- DatasourceRoutingProvider 零侵入路由集成
- 前端 DatasourcePanel（CRUD + 映射配置）

**Phase 3: 规则版本与发布管理**
- 变更审批工作流（auto/manual 模式）
- 环境隔离：dev / staging / prod
- 灰度发布：白名单 / 用户比例 / 随机百分比
- 结构化 Diff API（side-by-side 可视化）
- 一键回滚 + 应用层灰度路由
- 陪跑流量重放 + ShadowComparisonService 4 维度自动对比
- Flyway schema V3.1.0 ~ V3.14.0

**Phase 4: 下游 Agent 分析**
- 决策日志聚合分析 API（/analysis/*，7 个端点）
- 规则内容导出 API（/export/*，4 个端点）
- 偏差检测：7 天日均值基线 + sigma 阈值异常检测
- 规则覆盖率分析：热/冷/死规则分类、触发频率分布
- 前端分析仪表盘：三 Tab（决策趋势、规则覆盖、偏差检测）+ ECharts
- ruleforge CLI（Node.js）：analysis + export 命令组
- Claude Code Skills（6 个）
- 测试覆盖：100 个测试（后端 40 + 前端 41 + CLI 19）
