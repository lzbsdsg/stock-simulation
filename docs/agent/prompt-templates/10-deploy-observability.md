# Skill Card: 部署与可观测性（Deploy & Observability）

## 触发句式

> 请帮我生成本地开发环境的部署与可观测性方案，包含 Dockerfile、docker-compose.dev、Nginx、Prometheus、Grafana、日志配置、GitHub Actions CI。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 容器运行时 | Docker + docker-compose |
| 反向代理 | Nginx |
| CI/CD | GitHub Actions |
| 日志 | Logback JSON → Loki (可选) |
| 指标 | Micrometer → Prometheus → Grafana |
| 告警 | Grafana Alert Rules (最小可行) |
| 镜像仓库 | 可选（本地开发可不启用） |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 本地开发环境（Windows/macOS/Linux）、端口占用、HTTP 访问
2. **目录树** — 项目根目录下的部署相关文件
3. **核心文件**
   - `Dockerfile` — 多阶段构建（Maven build → JRE runtime）
   - `docker-compose.dev.yml` — 本地开发完整编排（app + pg + redis + rabbitmq + nginx + prometheus + grafana）
   - `nginx/nginx.conf` — 反向代理 + 前端静态文件
   - `.github/workflows/ci.yml` — 构建 + 测试 + 镜像推送
   - `.github/workflows/ci.yml` — 本地开发构建与测试
   - `prometheus/prometheus.yml` — 指标采集配置
   - `grafana/dashboards/` — 预置Dashboard JSON
   - `src/main/resources/logback-spring.xml` — JSON格式日志
4. **关键指标** — 必须采集的指标列表
   - JVM: heap, gc, threads
   - HTTP: request count, latency p50/p95/p99, error rate
   - Business: order count, trade count, active users
   - Infrastructure: PG connections, Redis hit rate, MQ queue depth
5. **告警规则** — 最少5条告警
   - HTTP 5xx rate > 1% for 5min
   - API P99 > 500ms for 5min
   - JVM heap > 80% for 10min
   - PG connection pool exhausted
   - MQ queue depth > 1000

---

## 质量验收标准

- [ ] docker compose -f docker-compose.dev.yml up 一键启动全部服务
- [ ] CI 流水线≤10min完成
- [ ] Prometheus 可采集到应用指标
- [ ] Grafana Dashboard 可展示核心指标
- [ ] 日志为结构化JSON格式

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 镜像太大 | 多阶段构建 + distroless/slim 基础镜像 |
| 密钥泄漏 | 用GitHub Secrets + 环境变量，不入镜像 |
| 端口冲突 | docker-compose统一端口映射 |
| 日志太多 | 配置合理的日志级别 + 日志轮转 |
| 指标基数爆炸 | 限制 label 的 cardinality |
