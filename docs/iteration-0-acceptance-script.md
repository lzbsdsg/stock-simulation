# Iteration 0 验收脚本（Windows CMD）

> 目标：按开发文档对基础设施、后端、前端、关键访问路径进行一键化验收。

## 0. 进入项目目录

```bat
cd /d d:\StockSimulation\stock-simulation
```

## 1. 启动基础设施

```bat
docker compose -f docker-compose.dev.yml up -d
```

预期：PG 主从、Redis 6 节点、RabbitMQ、Nginx 全部启动。

## 2. 检查容器状态

```bat
docker compose -f docker-compose.dev.yml ps
```

预期：核心服务为 `Up`，数据库与 Redis 节点状态为 `healthy`。

## 3. 验证 Nginx 配置

```bat
docker exec stock-nginx nginx -t
```

预期：`syntax is ok` 和 `test is successful`。

## 4. 验证 Redis Cluster

```bat
docker exec -it stock-redis-1 redis-cli --askpass -p 7000 cluster info
```

预期：输入密码后输出 `cluster_state:ok`、`cluster_slots_ok:16384`。

## 5. 验证 RabbitMQ 运行状态

```bat
docker exec stock-rabbitmq rabbitmq-diagnostics check_running
```

预期：`fully booted and running`。

## 6. 后端编译检查

```bat
call .\mvnw.cmd -DskipTests compile
```

预期：`BUILD SUCCESS`。

## 7. 启动后端（dev）

```bat
call .\mvnw.cmd spring-boot:run -Dspring.profiles.active=dev
```

PowerShell 推荐写法（避免 `-Dspring.profiles.active` 被误解析）：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

预期：日志出现 `Tomcat started on port 8080`。

如果 8080 被占用：

```bat
netstat -ano | findstr LISTENING | findstr ":8080"
tasklist /fi "PID eq <PID>"
```

## 8. HTTP 验收（Swagger / Prometheus）

```bat
curl -s -o NUL -w "URL=http://localhost/swagger-ui/index.html STATUS=%%{http_code}\n" http://localhost/swagger-ui/index.html
curl -s -o NUL -w "URL=http://localhost/actuator/prometheus STATUS=%%{http_code}\n" http://localhost/actuator/prometheus
curl -s -o NUL -w "URL=http://localhost:8080/actuator/health STATUS=%%{http_code}\n" http://localhost:8080/actuator/health

curl -s http://localhost/swagger-ui/index.html | findstr /i "Swagger UI"
curl -s http://localhost/actuator/prometheus | findstr /i "# HELP"
curl -s http://localhost:8080/actuator/health
```

预期：
- `http://localhost/swagger-ui/index.html` 返回 200，正文包含 `Swagger UI`
- `http://localhost/actuator/prometheus` 返回 200，正文包含 Prometheus 指标文本
- `http://localhost:8080/actuator/health` 可达（200 或 503 都代表接口可访问；503 表示健康状态 DOWN）

## 9. 前端依赖与质量检查

```bat
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm install
pnpm lint
pnpm build
```

预期：`lint` 通过，`build` 成功产出 `dist`。

## 10. 启动前端开发服务

```bat
pnpm dev --host 0.0.0.0 --port 5173
```

预期：终端显示 `http://localhost:5173/`。

## 11. 端口连通性补充检查

```bat
netstat -ano | findstr LISTENING | findstr ":80"
netstat -ano | findstr LISTENING | findstr ":8080"
netstat -ano | findstr LISTENING | findstr ":5173"
```

预期：三个端口都能查到 `LISTENING`。

## 12. 清理环境（可选）

```bat
cd /d d:\StockSimulation\stock-simulation
docker compose -f docker-compose.dev.yml down
```

前端/后端进程可在对应终端中 `Ctrl + C` 停止。
