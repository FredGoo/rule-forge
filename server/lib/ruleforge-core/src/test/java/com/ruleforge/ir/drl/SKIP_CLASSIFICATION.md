# DrlGrammarSmokeTest — 10 @Disabled 分类

V5.49.1 排查结果:10/10 都是 **V5.42.1 grammar 边缘**。
V5.50 sprint 启动时改名 **V5.50 sprint owner**(V5.42.5 已合并到 V5.50)。

## 分类 (按根因)

### A. LL(*) 决策冲突 (3 个) — grammar rule 顺序问题
需要重新组织 ANTLR4 grammar rule 或拆 sub-rule 消歧。

| Test | 边缘 | V5.50 收口 |
|---|---|---|
| `lhsFrom` | `pattern from expression` 的 binding prefix 解析冲突 | **V5.50.1 P0** |
| `lhsCollect` | `from collect(...)` 同 from 解析 | **V5.50.1 P0** |
| `stringMethods` | `name[starts-with "Mr"]` pattern 内 stringMethod | **V5.50.1 P0** (联动) |

### B. 简化版 grammar (4 个) — V5.42.1 故意没写完整
原 plan 决定先支持 happy path,边缘 case 留给后续 sprint。

| Test | 边缘 | V5.50 收口 |
|---|---|---|
| `lhsAccumulateCount` | accumulate 5 内置(count/sum/avg/min/max)init/action/result 3 段 | **V5.50.1 P0** |
| `lhsAccumulateSum` | 同上,sum 版 | V5.50.3 P2 |
| `allOperators` | pattern 内 13 种 op(`&&`,`||`,`in`,`not in`,`matches`,`contains`,`memberOf`,`soundslike`...) | V5.50.2 P1 |
| `rhsStatements` | update / bare function / 完整 methodCall | **V5.50.1 P0** |

### C. 顶层 statement 简化版 (3 个) — query / function / declare
V5.42.1 砍掉,只留 rule + accumulate + extends 这几个核心。

| Test | 边缘 | V5.50 收口 |
|---|---|---|
| `queryBasic` | `query "Q1"(Integer $min) ... end` parameter type 解析 | V5.50.3 P2 |
| `functionBasic` | `function Integer myFn(Integer x) { return x + 1; }` returnType 解析 | V5.50.3 P2 |
| `declareBasic` | `declare Applicant extends Person name : String age : Integer end` UPPER_IDENTIFIER | V5.50.4 P3 |

## V5.50 行动

10 个 skip 收口路径已分到 4 个 commit:
- **V5.50.1 P0**(5 个):lhsFrom / lhsCollect / rhsStatements / lhsAccumulateCount / stringMethods
- **V5.50.2 P1**(1 个):allOperators
- **V5.50.3 P2**(3 个):lhsAccumulateSum / queryBasic / functionBasic
- **V5.50.4 P3**(1 个):declareBasic

每 commit 内部 unskip + grammar 联动改 + AST visitor override + Deserializer 扩方法。

## V5.50.1 WIP commit 状态

WIP commit `WIP: V5.50.1 P0 DRL grammar BDD scaffold` 已 push:
- ✅ 4 grammar test red(lhsFrom / lhsCollect / lhsAccumulateCount / stringMethods)
- ✅ 1 grammar test green(rhsStatements 回归 guard)
- ✅ AssertsAccumulateReverseStillRejected 1 pass(D3 不变量锁)
- ✅ PendingLhsMigration 1 pass(PENDING_LHS 字段仍在,等 caller 切走后清)

V5.50.1 真正 commit 改完 grammar → 4 red 转 green,2 lock-in 仍 pass。详细 diff plan 见
`docs-site/ci/v5.50.1-grammar-diff-plan.md`。

## V5.49 验证(已修基线,留着参考)

```bash
mvn -pl server/lib/ruleforge-core test -Dtest=DrlGrammarSmokeTest
# V5.49 期望: 22 tests / 0 fail / 10 skip(维持) / 0 error
# V5.50.1 WIP 期望: 32 tests / 4 fail / 10 skip / 18 pass(4 red 是预期的 grammar work 范围)
# V5.50.4 终态期望: 32 tests / 0 fail / 0 skip
```
```
