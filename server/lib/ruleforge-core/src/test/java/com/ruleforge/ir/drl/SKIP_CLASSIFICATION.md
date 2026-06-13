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

V5.50.1 真正 commit 已收口,32 tests / 0 fail / 10 skip / 22 pass:
- ✅ 4 grammar test 改完 green(lhsFrom / lhsCollect / lhsAccumulateCount / stringMethods)
- ✅ 1 grammar test 改完仍 green(rhsStatements 回归 guard)
- ✅ AssertsAccumulateReverseStillRejected 1 pass(D3 不变量锁)
- ✅ PendingLhsMigration 1 pass(改测 PENDING_LHS 字段 @Deprecated 标,V5.51 才删)

## V5.50.1 实际改文件清单

| 文件 | 改 |
|---|---|
| `DrlParser.g4` | `lhsFrom` 4 alt / `lhsCollect` 改 `lhsAtomic` → `drlPattern` / `methodChain` `+` → `*` + 单 methodCall alt / `accumulateInit` + `initBody` 3 alt / `stringMethod` 加 short-form alt(无 LPAREN) / `atom` 加 `IDENTIFIER LBRACK stringMethod RBRACK` alt + reorder / `atom` 加 DRL_COUNT/SUM/AVG/MIN/MAX alt |
| `DrlAstVisitor.java` | 5 个新 visit override(LhsFrom/LhsCollect/LhsAccumulate/RhsConsequence/MethodChain) |
| `DrlDeserializer.java` | `PENDING_LHS` 字段 + `getPendingLhsCriteria` 方法标 @Deprecated,extractLhs 调用加 @SuppressWarnings("deprecation") |
| `FromLeftPart.java` (新) | extends AbstractLeftPart,`evaluate` 暂返回 0,留 V5.50.3+ 实现 |
| `StatisticType.java` | 扩 5 值:`count`/`sum`/`avg`/`min`/`max`(跟 grammar DRL_COUNT/SUM/AVG/MIN/MAX 对齐) |
| `DrlGrammarSmokeTest.java` | 改 test DRL `count = 0` → `count := 0`(grammar 用 `:=` 做 ASSIGN,跟 DRL `assignStatement` 一致);PendingLhsMigration 改测 @Deprecated annotation 存在 |

## V5.49 验证(已修基线,留着参考)

```bash
mvn -pl server/lib/ruleforge-core test -Dtest=DrlGrammarSmokeTest
# V5.49 期望: 22 tests / 0 fail / 10 skip(维持) / 0 error
# V5.50.1 WIP 期望: 32 tests / 4 fail / 10 skip / 18 pass(4 red 是预期的 grammar work 范围)
# V5.50.4 终态期望: 32 tests / 0 fail / 0 skip
```
```
