# RuleForge 规则引擎 — 风控审计文档

## 架构概览

RuleForge 基于 RETE 算法，nova 调用它做贷款审批、提现风控等决策。

```
nova-app → POST /api/loan/evaluate → RuleForge
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

## 执行流程

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

---

## Console API

> `{root}` 由 `ruleforgeV2.root.path` 配置

### 1. 决策评估

#### POST `/api/loan/evaluate`
生产入口。nova 调用。

**Request Body:**
```json
{
  "userId": "123456",
  "orderNo": "WD20260513...",
  "rulePackagePath": "loan/approval",
  "flowId": "loan-approval-flow"
}
```
| 参数 | 必填 | 说明 |
|------|:--:|------|
| userId | ✅ | 用户ID |
| orderNo | | 订单号 |
| rulePackagePath | ✅ | 规则包路径 |
| flowId | ✅ | 决策流ID |

**Response:**
```json
{
  "success": true,
  "data": {
    "ruleResult": "APPROVE",
    "creditLimit": 5000.00,
    "product": "P001",
    "lockDays": null,
    "ifManualReview": false
  }
}
```

---

### 2. 项目管理

#### POST `/{root}/frame/loadProjects`

| 参数 | 默认 | 说明 |
|------|------|------|
| projectName | 全部 | 项目名，不传列出全部 |
| searchFileName | | 搜索文件名 |
| classify | | 是否分类显示 |
| types | all | 文件类型: lib/rule/table/tree/flow |
| projectDetail | true | 是否返回项目详情 |

**Response:** `{repo: Repository}` — Repository 含文件树、项目信息。

#### POST `/{root}/frame/fileSource`

| 参数 | 说明 |
|------|------|
| projectFile | 文件路径，格式 `projectName:filePath` |

**Response:** 文件内容 (String)。

#### POST `/{root}/frame/fileVersions`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

**Response:** `[{version, commitMessage, author, timestamp}]`

#### POST `/{root}/frame/fileExistCheck`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |
| project | 项目名 |

#### POST `/{root}/frame/createProject`

| 参数 | 说明 |
|------|------|
| projectName | 项目名称 |

#### POST `/{root}/frame/createFile`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |
| type | 文件类型 (Ruleset/DecisionTable/DecisionTree/RuleFlow...) |
| project | 项目名 |

#### POST `/{root}/frame/createFolder`

| 参数 | 说明 |
|------|------|
| filePath | 文件夹路径 |
| project | 项目名 |

#### POST `/{root}/frame/deleteProject`

| 参数 | 说明 |
|------|------|
| projectName | 项目名 |

#### POST `/{root}/frame/deleteFile`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

#### GET `/{root}/frame/exportProjectBackupFile`

| 参数 | 说明 |
|------|------|
| projectName | 项目名 |

**Response:** 下载 ZIP 文件。

#### POST `/{root}/frame/importProject`

**Request:** `multipart/form-data`，文件字段名 `file`

---

### 3. 规则包管理

#### POST `/{root}/packageeditor/loadPackages`

| 参数 | 说明 |
|------|------|
| project | 项目名 |
| env | 可选，指定环境 |

**Response:** `[ResourcePackage]` — 规则包列表，含规则集、决策表等。

#### POST `/{root}/packageeditor/loadPackageConfig`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

#### POST `/{root}/packageeditor/loadFlows`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

**Response:** 决策流列表。

#### POST `/{root}/packageeditor/loadForTestVariableCategories`

| 参数 | 说明 |
|------|------|
| files | 规则文件列表，逗号分隔 |

**Response:** `[VariableCategory]` — 变量分类列表。

#### POST `/{root}/packageeditor/saveResourcePackages`

保存规则包到仓库。

#### POST `/{root}/packageeditor/getPackageDiff`

| 参数 | 说明 |
|------|------|
| project | 项目名 |
| packageId | 规则包ID |
| version1, version2 | 两个版本号 |

**Response:** 差异内容。

#### POST `/{root}/packageeditor/getFileDiff`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

**Response:** Git diff 格式差异。

#### POST `/{root}/packageeditor/refreshKnowledgeCache`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

---

### 4. 决策流

#### GET `/{root}/ruleflowdesigner/loadFlowDefinition`

| 参数 | 说明 |
|------|------|
| file | 决策流文件路径 |

**Response:** 决策流 JSON，含 nodes(节点)、connections(连线)、ruleRefs(规则引用)。

---

### 5. 变量管理

#### POST `/{root}/variableeditor/generateFields`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

**Response:** 变量字段列表。

#### POST `/{root}/variableeditor/generateVariableLibrary`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

---

### 6. 测试规则

#### POST `/{root}/packageeditor/doTest`
**审计最常用。** 传输入参数，返回决策结果。

| 参数 | 说明 |
|------|------|
| project | 项目名 |
| files | 规则文件列表 |
| flowId | 决策流ID |
| inputParams | 输入参数 (JSON) |

**Response:** `ExecutionResponseImpl` — 含输出参数 Map + 执行消息列表。

#### POST `/{root}/packageeditor/doBatchTest`

批量测试，用 Excel 导入测试用例，批量执行并导出结果。

#### POST `/{root}/test/fast`

快速测试，传文件和输入参数。

#### POST `/{root}/test/variableCategories/load`

加载测试用变量分类。

#### POST `/{root}/test/data/appId`

查询测试数据 App ID。

---

### 7. 通用

#### POST `/{root}/common/loadXml`

| 参数 | 说明 |
|------|------|
| file | 文件路径 |

**Response:** 规则文件 XML 内容。

#### GET `/{root}/common/loadFunctions`

**Response:** 系统函数列表。

#### POST `/{root}/common/loadReferenceFiles`

| 参数 | 说明 |
|------|------|
| file | 文件路径 |

**Response:** 引用链文件列表。

#### POST `/{root}/common/checkFileDirty`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

**Response:** `{dirty: true/false}`

---

### 8. 知识加载

#### POST `/{root}/loadKnowledge/load/test`

测试加载知识包。

---

## 审计工作流

```
1. POST /api/loan/evaluate                → 手动重现决策（或用 DB decision_log 查历史）
2. POST /{root}/frame/loadProjects        → 找到目标项目
3. POST /{root}/packageeditor/loadFlows   → 找到决策流
4. GET  /{root}/ruleflowdesigner/loadFlowDefinition → 看决策流逻辑
5. POST /{root}/packageeditor/doTest      → 传入样本，验证输出
```
