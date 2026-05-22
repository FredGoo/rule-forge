# RuleForge Console Docker 部署指南

本文档描述了如何使用Docker和Docker Compose部署RuleForge Console的开发和生产环境。

## 目录结构

```
docker/
├── README.md                    # 本文档
├── entrypoint.sh               # 容器启动脚本
├── application-dev.yml         # 开发环境配置
├── application-prod.yml        # 生产环境配置
├── nginx/                      # Nginx配置（可选）
├── prometheus/                 # Prometheus配置（可选）
└── grafana/                    # Grafana配置（可选）
```

## 快速开始

### 1. 环境准备

确保已安装以下软件：
- Docker 20.10+
- Docker Compose 1.29+

### 2. 配置环境变量

复制环境变量模板并修改：

```bash
cp .env.example .env
# 编辑 .env 文件，修改数据库密码等配置
```

### 3. 启动服务

#### 方式一：使用Docker Compose管理脚本

```bash
# 启动所有服务
./docker-compose-manage.sh start

# 只启动开发环境
./docker-compose-manage.sh start dev

# 只启动生产环境
./docker-compose-manage.sh start prod
```

#### 方式二：直接使用Docker Compose

```bash
# 启动所有服务
docker-compose up -d

# 启动特定服务
docker-compose up -d mysql redis ruleforge-dev
```

### 4. 访问应用

- **开发环境**: http://localhost:8080
- **生产环境**: http://localhost:9090
- **管理端点**:
  - 开发环境: http://localhost:8081
  - 生产环境: http://localhost:9091
- **Grafana监控**: http://localhost:3000 (admin/admin_password)
- **Prometheus**: http://localhost:9090

## Docker镜像构建

### 使用构建脚本

```bash
# 构建开发环境镜像
./docker-build.sh --env dev

# 构建生产环境镜像
./docker-build.sh --env prod --tag v1.0.0

# 构建并推送到仓库
./docker-build.sh --env prod --push --registry registry.example.com

# 不使用缓存构建
./docker-build.sh --env dev --no-cache
```

### 手动构建

```bash
# 构建开发环境
docker build --build-arg BUILD_ENV=dev -t ruleforge-console:dev .

# 构建生产环境
docker build --build-arg BUILD_ENV=prod -t ruleforge-console:prod .
```

## 服务管理

### 查看服务状态

```bash
# 使用管理脚本
./docker-compose-manage.sh status

# 直接使用Docker Compose
docker-compose ps
```

### 查看日志

```bash
# 查看所有服务日志
./docker-compose-manage.sh logs

# 查看特定服务日志
./docker-compose-manage.sh logs dev
./docker-compose-manage.sh logs mysql

# 实时跟踪日志
docker-compose logs -f ruleforge-dev
```

### 进入容器

```bash
# 进入开发环境容器
./docker-compose-manage.sh shell ruleforge-dev

# 进入MySQL容器
./docker-compose-manage.sh shell mysql

# 进入Redis容器
./docker-compose-manage.sh shell redis
```

### 数据库迁移

```bash
# 执行数据库迁移
./docker-compose-manage.sh migrate

# 或直接调用API
curl -X POST http://localhost:8081/migration/migrate
```

## 配置说明

### 环境变量

主要环境变量说明：

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| DB_HOST | 数据库主机 | mysql |
| DB_PORT | 数据库端口 | 3306 |
| DEV_DB_NAME | 开发环境数据库名 | ruleforge_dev |
| PROD_DB_NAME | 生产环境数据库名 | ruleforge_prod |
| MYSQL_ROOT_PASSWORD | MySQL root密码 | root_password |
| DEV_JAVA_OPTS | 开发环境JVM参数 | -Xmx1024m -Xms512m |
| PROD_JAVA_OPTS | 生产环境JVM参数 | -Xmx2048m -Xms1024m |

### 应用配置

#### 开发环境 (application-dev.yml)
- 日志级别: DEBUG
- 数据库连接池: 最大20
- 启用详细SQL日志
- 管理端点完全开放

#### 生产环境 (application-prod.yml)
- 日志级别: INFO
- 数据库连接池: 最大50
- 禁用SQL日志
- 管理端点限制访问

## 端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| ruleforge-dev | 8080 | 开发环境应用 |
| ruleforge-dev | 8081 | 开发环境管理端点 |
| ruleforge-prod | 9090 | 生产环境应用 |
| ruleforge-prod | 9091 | 生产环境管理端点 |
| mysql | 3306 | MySQL数据库 |
| redis | 6379 | Redis缓存 |
| nginx | 80/443 | 反向代理 |
| prometheus | 9090 | 监控数据收集 |
| grafana | 3000 | 监控可视化 |

## 数据持久化

以下目录映射到主机，确保数据持久化：

- `mysql_data`: MySQL数据
- `redis_data`: Redis数据
- `./logs/dev`: 开发环境日志
- `./logs/prod`: 生产环境日志
- `prometheus_data`: Prometheus数据
- `grafana_data`: Grafana配置

## 生产环境部署

### 1. 安全配置

```bash
# 修改默认密码
export MYSQL_ROOT_PASSWORD=your_secure_password
export REDIS_PASSWORD=your_redis_password
export GRAFANA_ADMIN_PASSWORD=your_grafana_password
```

### 2. 资源限制

在 `docker-compose.yml` 中添加资源限制：

```yaml
services:
  ruleforge-prod:
    deploy:
      resources:
        limits:
          memory: 4G
          cpus: '2.0'
        reservations:
          memory: 2G
          cpus: '1.0'
```

### 3. 启用HTTPS

配置Nginx SSL证书：

```bash
# 将证书文件放入 docker/nginx/ssl/
# 修改 docker/nginx/conf.d/ruleforge.conf
```

### 4. 监控配置

- Prometheus: 收集应用和系统指标
- Grafana: 可视化监控数据
- 健康检查: 自动检测服务状态

## 故障排除

### 常见问题

1. **服务启动失败**
   ```bash
   # 检查日志
   docker-compose logs service_name

   # 检查端口占用
   netstat -tulpn | grep port
   ```

2. **数据库连接失败**
   ```bash
   # 检查MySQL状态
   docker-compose exec mysql mysqladmin ping

   # 检查网络连接
   docker network ls
   ```

3. **内存不足**
   ```bash
   # 调整JVM参数
   export JAVA_OPTS="-Xmx512m -Xms256m"
   ```

4. **磁盘空间不足**
   ```bash
   # 清理Docker资源
   docker system prune -a
   ```

### 日志位置

- 应用日志: `./logs/{env}/ruleforge-{env}-console.log`
- Docker日志: `docker-compose logs service_name`
- 系统日志: `/var/log/docker/`

## 性能优化

### 1. JVM调优

根据服务器规格调整JVM参数：

```bash
# 开发环境
DEV_JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseG1GC"

# 生产环境
PROD_JAVA_OPTS="-Xmx4096m -Xms2048m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### 2. 数据库优化

- 配置合适的连接池大小
- 启用查询缓存
- 定期执行数据库维护

### 3. 缓存策略

- Redis缓存热点数据
- 应用层缓存配置
- CDN静态资源缓存

## 备份和恢复

### 数据库备份

```bash
# 备份开发环境数据库
docker-compose exec mysql mysqldump -u root -p ruleforge_dev > backup_dev.sql

# 备份生产环境数据库
docker-compose exec mysql mysqldump -u root -p ruleforge_prod > backup_prod.sql
```

### 数据恢复

```bash
# 恢复数据库
docker-compose exec -i mysql mysql -u root -p ruleforge_dev < backup_dev.sql
```

### 配置备份

```bash
# 备份配置文件
tar -czf ruleforge-config-backup.tar.gz .env docker/ docker-compose.yml
```

## 更新和升级

### 1. 更新镜像

```bash
# 拉取最新镜像
docker-compose pull

# 重新构建镜像
./docker-build.sh --env prod --tag latest

# 重启服务
./docker-compose-manage.sh restart prod
```

### 2. 数据库迁移

```bash
# 执行Flyway迁移
./docker-compose-manage.sh migrate
```

## 支持和联系

如遇到问题，请：

1. 查看本文档的故障排除部分
2. 检查应用和系统日志
3. 确认配置文件正确性
4. 联系技术支持团队