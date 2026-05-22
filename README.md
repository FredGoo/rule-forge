# RuleForge

基于 RETE 算法的 Java 规则引擎，支持多种规则定义方式和可视化规则设计器。

## 特性

- 向导式规则集、脚本式规则集、决策表、决策树、评分卡、决策流
- 可视化 Web 规则设计器
- RETE 算法高性能规则执行
- 规则热部署和动态更新
- 基于 Spring Boot 4.0，Java 17

## 模块结构

| 模块 | 说明 |
|------|------|
| ruleforge-core | 规则引擎核心：RETE 算法、规则解析、知识库 |
| ruleforge-console | 编辑器业务：REST API、项目管理、知识包管理 |
| ruleforge-executor | 执行器业务：规则执行、知识包接收 |
| ruleforge-console-app | 可部署的编辑器应用（端口 8081） |
| ruleforge-executor-app | 可部署的执行器应用（端口 8082） |
| frontend | React 可视化规则设计器 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+

### 编译

```bash
cd backend
mvn clean compile
```

### 配置

编辑 `backend/ruleforge-console-app/src/main/resources/application.yml`，填入数据库连接信息。

### 启动

```bash
# 启动编辑器
cd backend/ruleforge-console-app
mvn spring-boot:run

# 启动执行器
cd backend/ruleforge-executor-app
mvn spring-boot:run

# 启动前端
cd frontend
npm install
npm start
```

编辑器 `http://localhost:8081`，执行器 `http://localhost:8082`。

## License

Apache-2.0
