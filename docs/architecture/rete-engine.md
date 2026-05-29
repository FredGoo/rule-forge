# RETE 引擎

## RETE 网络构建

### 关键类

- `Rete` — RETE 网络容器，持有 ObjectTypeNodes 和规则分组
- `ReteBuilder` — 从规则构建 RETE 网络
- `BuildContext` — 构建上下文，负责 ID 生成
- `BaseReteNode` — 所有 RETE 节点类型的抽象基类（And, Or, Criteria 等）

### 构建过程

1. 规则被解析后按 activation groups 和 agenda groups 分组
2. `ReteBuilder.buildRete()` 构建网络：
   - 为每种 fact 类型创建 ObjectTypeNode
   - 使用 CriterionBuilder 构建条件分支
   - 连接到 TerminalNode（规则结论）
   - 将规则组织到 activation/agenda 分组
3. 共享公共子模式以优化网络

## KnowledgeSession 生命周期

### 关键类

- `KnowledgeSession` / `KnowledgeSessionImpl` — 会话接口与实现
- `KnowledgeSessionFactory` — 会话工厂
- `WorkingMemory` — Fact 管理接口
- `Agenda` — 规则激活与执行管理

### 生命周期

1. **创建**: `KnowledgeSessionFactory.newKnowledgeSession()` 从 KnowledgePackage 创建会话
2. **Fact 插入**: `insert()` → `assertFact()` → `ReteInstance.enter()`，Fact 通过 ObjectTypeActivities 流入条件节点
3. **规则触发**: 匹配产生 Activation 放入 Agenda，`Agenda.execute()` 按 salience 和过滤器处理激活，执行动作并更新工作内存

## 规则类型处理

所有规则类型通过各自的反序列化器转换为内部模型，再由 `ReteBuilder` 编译到 RETE 网络：

| 规则类型 | 解析器 | 说明 |
|---------|--------|------|
| 规则集 | `RuleSetParser` / `RuleSetDeserializer` | 标准产生式规则 |
| 决策表 | `DecisionTableParser` / `DecisionTableDeserializer` | 转换为规则集处理 |
| 决策树 | `DecisionTreeRulesBuilder` | 构建 LHS 条件 |
| 评分卡 | `ComplexScorecardParser` / `ScorecardDeserializer` | 加权评分模型 |

## 知识包加载与缓存

### 关键类

- `KnowledgePackage` / `KnowledgePackageImpl` — 知识包接口与实现（含 RETE 网络和规则元数据）
- `KnowledgeCache` / `MemoryKnowledgeCache` — 缓存接口与内存实现

### 加载过程

1. 规则定义解析为模型对象
2. `ReteBuilder` 编译为 RETE 网络
3. 创建包含网络、元数据和版本信息的知识包
4. 通过 `KnowledgeCache` 缓存

### 缓存特性

- `markKnowledgeDirty()` 触发缓存失效
- 支持按项目名移除缓存
- 基于时间戳的版本管理
- 会话级别的知识包共享

## 核心交互

```
规则 ──→ ReteBuilder ──→ Rete 网络
                              │
KnowledgeSession ──→ ReteInstance (运行时网络)
                              │
Fact 插入 ──→ ObjectTypeNode ──→ 条件匹配
                              │
匹配结果 ──→ Activation ──→ Agenda
                              │
动作执行 ──→ WorkingMemory 更新
```
