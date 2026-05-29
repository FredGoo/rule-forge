# Executor REST API

## 测试执行

### GET `/test/do`

执行指定知识路径的规则。

| 参数 | 必填 | 说明 |
|------|:--:|------|
| path | ✅ | 知识包路径 |
| flow | | 决策流名称，提供后通过 Flowable BPMN 引擎执行 |

## 知识包管理

### POST `/test/knowledge`

标记知识包为脏（dirty），下次访问时强制重新编译。

**Request Body:**

```json
{
  "packageId": "package-id-here"
}
```
