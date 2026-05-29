# Console REST API

> `{root}` 由 `ruleforgeV2.root.path` 配置

## 1. 决策评估

### POST `/api/loan/evaluate`

生产入口。上游系统调用。

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

## 2. 项目管理

### POST `/{root}/frame/loadProjects`

| 参数 | 默认 | 说明 |
|------|------|------|
| projectName | 全部 | 项目名，不传列出全部 |
| searchFileName | | 搜索文件名 |
| classify | | 是否分类显示 |
| types | all | 文件类型: lib/rule/table/tree/flow |
| projectDetail | true | 是否返回项目详情 |

**Response:** `{repo: Repository}` — Repository 含文件树、项目信息。

### POST `/{root}/frame/fileSource`

| 参数 | 说明 |
|------|------|
| projectFile | 文件路径，格式 `projectName:filePath` |

**Response:** 文件内容 (String)。

### POST `/{root}/frame/fileVersions`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

**Response:** `[{version, commitMessage, author, timestamp}]`

### POST `/{root}/frame/fileExistCheck`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |
| project | 项目名 |

### POST `/{root}/frame/createProject`

| 参数 | 说明 |
|------|------|
| projectName | 项目名称 |

### POST `/{root}/frame/createFile`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |
| type | 文件类型 (Ruleset/DecisionTable/DecisionTree/RuleFlow...) |
| project | 项目名 |

### POST `/{root}/frame/createFolder`

| 参数 | 说明 |
|------|------|
| filePath | 文件夹路径 |
| project | 项目名 |

### POST `/{root}/frame/deleteProject`

| 参数 | 说明 |
|------|------|
| projectName | 项目名 |

### POST `/{root}/frame/deleteFile`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

### GET `/{root}/frame/exportProjectBackupFile`

| 参数 | 说明 |
|------|------|
| projectName | 项目名 |

**Response:** 下载 ZIP 文件。

### POST `/{root}/frame/importProject`

**Request:** `multipart/form-data`，文件字段名 `file`

---

## 3. 规则包管理

### POST `/{root}/packageeditor/loadPackages`

| 参数 | 说明 |
|------|------|
| project | 项目名 |
| env | 可选，指定环境 |

**Response:** `[ResourcePackage]` — 规则包列表，含规则集、决策表等。

### POST `/{root}/packageeditor/loadPackageConfig`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

### POST `/{root}/packageeditor/loadFlows`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

**Response:** 决策流列表。

### POST `/{root}/packageeditor/loadForTestVariableCategories`

| 参数 | 说明 |
|------|------|
| files | 规则文件列表，逗号分隔 |

**Response:** `[VariableCategory]` — 变量分类列表。

### POST `/{root}/packageeditor/saveResourcePackages`

保存规则包到仓库。

### POST `/{root}/packageeditor/getPackageDiff`

| 参数 | 说明 |
|------|------|
| project | 项目名 |
| packageId | 规则包ID |
| version1, version2 | 两个版本号 |

**Response:** 差异内容。

### POST `/{root}/packageeditor/getFileDiff`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

**Response:** Git diff 格式差异。

### POST `/{root}/packageeditor/refreshKnowledgeCache`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

---

## 4. 决策流

### GET `/{root}/ruleflowdesigner/loadFlowDefinition`

| 参数 | 说明 |
|------|------|
| file | 决策流文件路径 |

**Response:** 决策流 JSON，含 nodes(节点)、connections(连线)、ruleRefs(规则引用)。

---

## 5. 变量管理

### POST `/{root}/variableeditor/generateFields`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

**Response:** 变量字段列表。

### POST `/{root}/variableeditor/generateVariableLibrary`

| 参数 | 说明 |
|------|------|
| project | 项目名 |

---

## 6. 测试规则

### POST `/{root}/packageeditor/doTest`

传输入参数，返回决策结果。

| 参数 | 说明 |
|------|------|
| project | 项目名 |
| files | 规则文件列表 |
| flowId | 决策流ID |
| inputParams | 输入参数 (JSON) |

**Response:** `ExecutionResponseImpl` — 含输出参数 Map + 执行消息列表。

### POST `/{root}/packageeditor/doBatchTest`

批量测试，用 Excel 导入测试用例，批量执行并导出结果。

### POST `/{root}/test/fast`

快速测试，传文件和输入参数。

### POST `/{root}/test/variableCategories/load`

加载测试用变量分类。

### POST `/{root}/test/data/appId`

查询测试数据 App ID。

---

## 7. 通用接口

### POST `/{root}/common/loadXml`

| 参数 | 说明 |
|------|------|
| file | 文件路径 |

**Response:** 规则文件 XML 内容。

### GET `/{root}/common/loadFunctions`

**Response:** 系统函数列表。

### POST `/{root}/common/loadReferenceFiles`

| 参数 | 说明 |
|------|------|
| file | 文件路径 |

**Response:** 引用链文件列表。

### POST `/{root}/common/checkFileDirty`

| 参数 | 说明 |
|------|------|
| filePath | 文件路径 |

**Response:** `{dirty: true/false}`

---

## 8. 知识加载

### POST `/{root}/loadKnowledge/load/test`

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
