# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Maven-based Java 17 project, Spring Boot 4.0.6. Run from `backend/`:

```bash
mvn compile                       # Compile all modules
mvn compile -pl ruleforge-core    # Compile single module with deps (-am)
mvn clean package -DskipTests     # Package without tests
```

Frontend is in `frontend/`, check its package.json for npm commands.

## Project Architecture

```
ruleforge-parent        Maven parent POM, Spring Boot BOM, Java 17
ruleforge-core          RETE rule engine (parsing, execution, knowledge base)
ruleforge-console       Web console business logic (controllers, services, DB)
ruleforge-executor      Rule execution engine (test endpoints, knowledge package)
ruleforge-console-app   Deployable Spring Boot app — editor (port 8081)
ruleforge-executor-app  Deployable Spring Boot app — executor (port 8082)
frontend                React-based visual rule designer
```

Dependency chain:
```
core ← console ← console-app
core ← executor ← executor-app
```

## Module Details

### ruleforge-core (`com.ruleforge.*`)
Pure engine, no Spring Boot dependency:
- `model/` — rule models (rule, table, tree, scorecard, flow, library)
- `model.rete/` — RETE algorithm implementation
- `runtime/` — knowledge session, execution, caching
- `parse/` — XML/DSL rule parsers (ANTLR4)
- `controller/` — KnowledgePackageReceiverServlet

### ruleforge-console (`com.ruleforge.console.*`)
Web editor backend:
- `controller/` — REST controllers (frame, common, package, flow)
- `service/` — repository, permission, test services
- `storage/` — project storage (DB-backed)
- `mapper/` — MyBatis-Plus mappers
- `repository/` — model classes (RepositoryFile, VersionFile, ResourcePackage, etc.)
- `servlet/` — utilities (RequestContext, ErrorInfo, ScriptType)
- `config/` — MybatisPlusConfig, FlywayConfig

### ruleforge-executor (`com.ruleforge.executor.*`)
Rule execution:
- `controller/TestController` — `/test/do`, `/test/knowledge`
- `service/` — RuleForgeService, KnowledgePackageServiceImpl
- `service/impl/ExecResourceProvider` — fetches resources from console via HTTP

### ruleforge-console-app (`com.ruleforge.console.app.*`)
Deployable editor with datasource config, environment provider, and business-specific code (loan decision, shadow execution, decision logging).

### ruleforge-executor-app (`com.ruleforge.executor.app.*`)
Deployable executor with RestTemplate config for console communication.

## Key Technologies

- Java 17, Spring Boot 4.0.6, Spring Framework 7
- MyBatis-Plus 3.5.9, MySQL, Flyway
- ANTLR4, Jackson, fastjson2, HikariCP
- Frontend: React

## Rule Types

向导式规则集, 脚本式规则集 (UL), 决策表, 脚本决策表, 决策树, 评分卡, 决策流

## Configuration

- Console-app: port 8081, dual datasource (app + ruleforge), `ruleforge.exec.url` → executor
- Executor-app: port 8082, `ruleforge.console.url` → console
