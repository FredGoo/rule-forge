# 规则测试

## 单条测试

通过 `/{root}/packageeditor/doTest` 接口，传入规则文件和输入参数，验证输出结果。

**参数：**

| 参数 | 说明 |
|------|------|
| project | 项目名 |
| files | 规则文件列表 |
| flowId | 决策流ID |
| inputParams | 输入参数 (JSON) |

**返回：** 输出参数 Map + 执行消息列表。

## 快速测试

通过 `/{root}/test/fast` 接口，快速传入文件和输入参数执行。

## 批量测试

通过 `/{root}/packageeditor/doBatchTest` 接口：

1. 用 Excel 导入测试用例
2. 批量执行
3. 导出结果

## 测试数据

- `/{root}/test/variableCategories/load` — 加载测试用变量分类
- `/{root}/test/data/appId` — 查询测试数据 App ID

## 审计重现

如需重现历史决策：

```
1. 从 decision_log 表查询历史记录
2. POST /api/loan/evaluate 传入相同参数重现
3. 对比结果差异
```
