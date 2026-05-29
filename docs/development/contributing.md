# 贡献指南

## 分支策略

- `main` 分支为稳定分支
- 功能开发使用 feature 分支，命名格式：`feature/xxx`
- Bug 修复使用 fix 分支，命名格式：`fix/xxx`

## 编码规范

- Java 17，使用 Spring Boot 4.0.6 和 Spring Framework 7
- 数据库访问使用 MyBatis-Plus 3.5.9
- 规则解析使用 ANTLR4
- 序列化优先使用 Jackson，部分场景使用 fastjson2

## 开发流程

1. TDD/BDD：先写测试行为定义（Gherkin Given/When/Then 注解），确认后再写测试代码，最后写实现代码
2. 提交前确保 `mvn compile` 通过
3. PR 需经过 code review 后合并

## 提交信息

使用简洁的英文描述，说明改动目的。例如：

```
Fix rule editor showing "undefined" for rules without remark
Add batch test export feature
```
