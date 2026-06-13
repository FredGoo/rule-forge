# Frontend E2E 闸门 + 168h Soak 推迟说明

> V5.50 范围。V5.51+ 才补 soak 文件占位。

## 3 个 e2e workflow(V5.50 收)

| workflow | 触发 | spec 子集 | browser | 用途 | 失败挡 merge? |
|---|---|---|---|---|---|
| `e2e-pr-smoke.yml` | PR paths `console-ui/**` | 7 spec:app / login / frame-navigation / datasource-panel / decision-table-editor / rule-editor / package-editor | chromium | PR gate(快) | ❌ info-only |
| `e2e-nightly-full.yml` | cron `30 6 * * *` UTC | 23 真 spec | chromium + firefox + webkit | 3 browser nightly | ❌ |
| `e2e-tour-nightly.yml` | cron `0 7 * * *` UTC | 13 tour spec(`_*-tour.spec.ts`) | chromium | tour nightly | ❌ |

3 个 workflow 都跑在 self-hosted `sit-336` runner(跟 frontend-deploy 共用)。

## 本地怎么跑

```bash
cd console-ui
npm run test:e2e           # 全 23 spec(走默认 playwright.config.js,3 browser)
npm run test:e2e:smoke     # 7 spec PR smoke 子集
npm run test:e2e:tour      # 13 tour spec
```

需要本地 backend 起好:
```bash
docker compose up -d mysql console-app executor-app
# 等 30s,健康检查:
curl -sf http://localhost:8180/ruleforge/  # console-app
```

`PLAYWRIGHT_BASE_URL` 默认 `http://localhost:3000`(vite dev),走 docker stack 时:
```bash
PLAYWRIGHT_BASE_URL=http://localhost npm run test:e2e:smoke
```

## 加新 spec 到 PR smoke 怎么改

如果某个新 spec 应该是 PR gate(覆盖核心 nav / auth / editor / release):

1. 编辑 `console-ui/playwright.config.smoke.js`,把 spec 加到 `testMatch` 数组
2. **不**直接加 `_*-tour.spec.ts` 后缀的(tour 走 nightly,PR 不该跑)
3. 跑 `npm run test:e2e:smoke` 验证子集还 ≤ 5min
4. PR 里说明为什么这个 spec 应该是 PR gate

如果 spec 是重量级(> 30s 跑一个),**不**加 — 留 nightly full。

## E2E seed 数据

`docker/init-sql/e2e_seed.sql` 启动时自动 INSERT IGNORE 进 `app_db.nd_datasource`:

| name | 用途 |
|---|---|
| `E2E待编辑数据源` | `datasource-panel.spec.ts` L174 "edit 名称" 用例的预置数据 |

新增 E2E seed 行要:
- name 前缀 `E2E%` 或 `e2e%`(命名空间隔离,prod 误跑不会污染)
- `INSERT IGNORE`(幂等)
- 在测试 spec 里说明为什么需要预置(不能 UI 创建)

## 168h stability soak 推迟清单(V5.51+ 才做)

V5.50 决定不做 168h soak,占位留给 V5.51 plan:
- `server/lib/ruleforge-core/src/test/java/com/ruleforge/rete/soak/ReteSteadyStateSoakTest.java`
- `server/lib/ruleforge-decision/src/test/java/com/ruleforge/decision/soak/FlowEngineSteadyStateSoakTest.java`
- parent/pom.xml `<profile>soak</profile>` block
- `.github/workflows/soak-168h.yml`

V5.51 plan 启动时决定 24h × 7 cron 还是 4h × 42 cron。
