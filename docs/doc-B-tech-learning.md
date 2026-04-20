# 文档 B：技术栈学习资料与指南

> 面向【一人全栈开发者】，按"先后端→后前端→再运维"顺序组织学习路径。

---

## 一、学习路径总览（6 周快速上手建议）

| 周次 | 主题 | 目标 |
|---|---|---|
| **Week 1** | Java 17 + Spring Boot 3.2 基础 | 能写出分层 REST CRUD |
| **Week 2** | Spring Security 6 + JWT + MyBatis-Plus | 完成认证模块 Demo |
| **Week 3** | PostgreSQL + Redis + Flyway | 建库建表 + 缓存读写 + 迁移管理 |
| **Week 4** | RabbitMQ + WebSocket | 消息发布消费 + WS推送 Demo |
| **Week 5** | Vue 3 + TypeScript + Vite + Pinia + ECharts | 前端架手架 + 行情页面 |
| **Week 6** | Docker + CI/CD + 监控 | 容器化部署 + GitHub Actions + Prometheus |

---

## 二、后端技术栈

### 2.1 Java 17 LTS

**学习目标**：掌握 Java 17 新特性在项目中的实际应用。

**核心知识点**：
- Records（用于 DTO/VO/Command 等不可变数据载体）
- Sealed Classes（用于 ErrorCode 层级、订单状态建模）
- Pattern Matching for instanceof
- Text Blocks（SQL、JSON 模板）
- Switch Expressions

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| Oracle 官方迁移指南 | https://docs.oracle.com/en/java/javase/17/migrate/ | 全文 |
| Baeldung Java 17 专题 | https://www.baeldung.com/java-17-new-features | Records, Sealed |
| 《Modern Java in Action》 | Manning 出版 | Ch1-3 Lambda/Stream, Ch17 Reactive |

**Minimum Demo**：
```java
// 用 Record 定义 Command
public record PlaceOrderCommand(
    String clientOrderId,
    String stockCode,
    OrderSide side,
    BigDecimal price,
    int quantity
) {}
```

---

### 2.2 Spring Boot 3.2.x

**学习目标**：掌握 Spring Boot 3.2 自动配置、Profile、Actuator、分层结构。

**核心知识点**：
- 自动配置原理（`@EnableAutoConfiguration`, `spring.factories` → `AutoConfiguration.imports`）
- Profile 多环境配置（dev / test）
- Actuator 端点（health, metrics, info, env）
- 异常处理（`@RestControllerAdvice` + `@ExceptionHandler`）
- 参数校验（`@Valid` + `jakarta.validation`）
- 日志配置（Logback + JSON 格式化）

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| Spring Boot 官方文档 | https://docs.spring.io/spring-boot/docs/3.2.x/reference/ | Getting Started, Web, Security, Actuator |
| Baeldung Spring Boot 系列 | https://www.baeldung.com/spring-boot | REST API, Error Handling, Profiles |
| Spring Guides | https://spring.io/guides | gs-rest-service, gs-validating-form-input |

**Minimum Demo**：
- 创建一个 REST Controller，实现 CRUD，返回统一 `Result<T>`
- 配置 `@RestControllerAdvice` 全局异常处理
- 使用 `application.yml` 与 `application-dev.yml` 配置

---

### 2.3 Spring Security 6 + JWT

**学习目标**：实现无状态 JWT 认证，掌握 Security Filter Chain 配置。

**核心知识点**：
- `SecurityFilterChain` Bean 配置（替代旧版 `WebSecurityConfigurerAdapter`）
- `OncePerRequestFilter` 自定义 JWT 校验过滤器
- `UserDetailsService` 自定义实现
- 路由级别权限控制（`requestMatchers().permitAll()` / `.authenticated()`）
- CORS 配置
- CSRF 禁用（前后端分离）
- JWT 签发与解析（jjwt 库 / nimbus-jose-jwt）
- Token 刷新（Refresh Token Rotation）

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| Spring Security 官方文档 | https://docs.spring.io/spring-security/reference/6.2/ | Architecture, Authentication, Authorization |
| Baeldung JWT专题 | https://www.baeldung.com/spring-security-oauth-jwt | 全文 |
| 《Spring Security in Action》第2版 | Manning | Ch2-5 Authentication, Ch7-9 Authorization |

**Minimum Demo**：
- 实现 `/auth/login` 返回 JWT
- 实现 JWT Filter 拦截并注入 `SecurityContext`
- 受保护接口 `/api/v1/user/me` 返回当前用户信息
- 登出时将 token 加入 Redis 黑名单

---

### 2.4 MyBatis-Plus 3.5.x

**学习目标**：掌握 CRUD 代码生成、条件构造器、分页与乐观锁插件。

**核心知识点**：
- `BaseMapper<T>` 通用 CRUD
- `LambdaQueryWrapper` 类型安全条件构造
- 分页插件配置（`MybatisPlusInterceptor` + `PaginationInnerInterceptor`）
- 乐观锁插件（`OptimisticLockerInnerInterceptor`）
- 自动填充（`MetaObjectHandler` → `created_at`, `updated_at`）
- 逻辑删除配置

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| MyBatis-Plus 官方文档 | https://baomidou.com/guide/ | 快速开始, CRUD接口, 条件构造器, 插件 |
| MyBatis-Plus GitHub | https://github.com/baomidou/mybatis-plus | Examples 目录 |
| MyBatis 官方文档 | https://mybatis.org/mybatis-3/zh/ | 映射器, 动态SQL（理解底层） |

**Minimum Demo**：
- `AccountMapper extends BaseMapper<AccountDO>` CRUD
- `LambdaQueryWrapper` 实现 "按用户ID查询+状态筛选" 带分页
- 配置乐观锁：更新时 `WHERE version = ?`；冲突时重试

---

### 2.5 MapStruct 1.5

**学习目标**：掌握编译期对象映射，消除手动 getter/setter 转换。

**核心知识点**：
- `@Mapper(componentModel = "spring")` Spring 集成
- 基础映射（同名字段自动映射）
- `@Mapping(source, target)` 字段名不同的映射
- 集合映射（List 转换）
- 嵌套对象映射
- 与 Lombok 配合（需 `lombok-mapstruct-binding`）

**推荐资源**：
| 资源 | 链接 |
|---|---|
| MapStruct 官方文档 | https://mapstruct.org/documentation/stable/reference/html/ |
| Baeldung MapStruct 教程 | https://www.baeldung.com/mapstruct |

**Minimum Demo**：
```java
@Mapper(componentModel = "spring")
public interface UserConverter {
    User toDomain(UserDO userDO);
    UserDO toDO(User user);
    UserVO toVO(User user);
}
```

---

### 2.6 Flyway 10

**学习目标**：版本化数据库迁移，团队协作有序。

**核心知识点**：
- 命名规范：`V{yyyyMMdd}_{seq}__{description}.sql`
- Spring Boot 自动集成（`spring.flyway.*` 配置）
- Baseline（对已有数据库初始化）
- Repair（修复失败的迁移）
- 禁止修改已执行的迁移文件

**推荐资源**：
| 资源 | 链接 |
|---|---|
| Flyway 官方文档 | https://documentation.red-gate.com/flyway/ |
| Baeldung Flyway 教程 | https://www.baeldung.com/database-migrations-with-flyway |

**Minimum Demo**：
- 创建 `V20260213_001__create_user_tables.sql`
- Spring Boot 启动时自动执行迁移
- 查看 `flyway_schema_history` 表确认版本

---

### 2.7 RabbitMQ 3.13

**学习目标**：掌握消息发布/消费、Exchange/Queue 绑定、ACK 机制。

**核心知识点**：
- AMQP 协议基本概念（Exchange, Queue, Binding, RoutingKey）
- Exchange 类型（Direct, Topic, Fanout）— 本项目主用 Direct
- 消息确认（Publisher Confirm + Consumer Manual ACK）
- 死信队列（DLX）配置
- Spring AMQP 集成（`RabbitTemplate` + `@RabbitListener`）
- 消息序列化（JSON MessageConverter）
- 幂等消费（Redis SETNX 防重复）

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| RabbitMQ 官方教程 | https://www.rabbitmq.com/tutorials | Tutorial 1-6（含 Java 示例） |
| Spring AMQP 文档 | https://docs.spring.io/spring-amqp/reference/ | Quick Tour, Sending, Receiving |
| 《RabbitMQ实战指南》 | 朱忠华 著 | Ch1-5 |

**Minimum Demo**：
- 定义 `trade.exchange` (Direct) + `trade.match.queue`
- Producer：下单后发送消息 `{orderId, stockCode, side, price, quantity}`
- Consumer：`@RabbitListener` 接收消息，打印到日志
- 配置 Manual ACK，模拟消费失败 → 重入队列

---

### 2.8 SpringDoc OpenAPI 3

**学习目标**：自动生成 API 文档 + Swagger UI。

**核心知识点**：
- `@Operation(summary)` 接口描述
- `@Tag(name)` 分组
- `@Schema(description)` 字段描述
- `@SecurityRequirement` JWT 标记
- Swagger UI 访问 `/swagger-ui/index.html`

**推荐资源**：
| 资源 | 链接 |
|---|---|
| SpringDoc 官方文档 | https://springdoc.org/ |
| Baeldung SpringDoc | https://www.baeldung.com/spring-rest-openapi-documentation |

---

## 三、数据库

### 3.1 PostgreSQL 16

**学习目标**：掌握建库建表、NUMERIC 精度、索引优化、事务隔离。

**核心知识点**：
- 数据类型：`BIGSERIAL`, `NUMERIC(18,2)`, `TIMESTAMPTZ`, `VARCHAR`, `BOOLEAN`, `TEXT`
- 索引：B-tree（默认）、部分索引（`WHERE status = 'PENDING'`）、联合索引
- 事务隔离级别（默认 Read Committed，金融场景理解 Serializable）
- `SELECT ... FOR UPDATE`（悲观锁）
- 窗口函数（收益排名、趋势分析）
- `EXPLAIN ANALYZE` 查询分析
- JSONB（灵活字段存储）

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| PostgreSQL 官方文档 | https://www.postgresql.org/docs/16/ | Ch5 数据定义, Ch8 数据类型, Ch11 索引, Ch13 并发控制 |
| 《PostgreSQL即学即用》| O'Reilly | Ch4-6 |
| pgExercises | https://pgexercises.com/ | 联合查询、窗口函数练习 |

**Minimum Demo**：
- 创建 `t_user_account` 表，插入数据
- 编写 `SELECT ... FOR UPDATE` 演示行锁
- 使用 `EXPLAIN ANALYZE` 分析查询计划
- 创建部分索引并验证查询优化效果

---

## 四、缓存

### 4.1 Redis 7.x

**学习目标**：掌握数据结构选型、TTL、分布式锁、Lua脚本。

**核心知识点**：
- 数据结构：String（验证码/JWT黑名单/幂等键）、Hash（行情快照）、ZSet（排行榜）
- TTL 策略（不同数据不同过期时间）
- 分布式锁（`SETNX` + Lua + Redisson 可选）
- Lua 脚本原子操作（令牌桶限流）
- Pipeline 批量操作
- Spring Data Redis + Lettuce 配置
- 连接池配置

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| Redis 官方文档 | https://redis.io/docs/ | Data types, Commands, Transactions |
| 《Redis设计与实现》 | 黄健宏 著 | Ch1-5 数据结构, Ch10 过期 |
| Redis University | https://university.redis.com/ | 免费在线课程 |
| Spring Data Redis | https://docs.spring.io/spring-data/redis/reference/ | RedisTemplate, Serialization |

**Minimum Demo**：
- String: 存取 OTP，设置 TTL=300s
- Hash: 存取行情快照 `HSET market:quote:sh600519 price 1888.88 change +2.35`
- Lua: 实现固定窗口限流脚本
- Spring: `RedisTemplate` CRUD + Jackson2JsonRedisSerializer

**本项目 Redis Key 设计**：

| Key Pattern | 数据结构 | TTL | 用途 |
|---|---|---|---|
| `otp:{email}` | String (BCrypt hash) | 300s | 邮箱验证码 |
| `otp:rate:{email}` | String (flag) | 60s | OTP发送频率限制 |
| `otp:ip:{ip}` | String (counter) | 3600s | IP发送限制 |
| `jwt:blacklist:{jti}` | String (1) | = access TTL 剩余 | JWT黑名单 |
| `jwt:refresh:{userId}` | String (tokenHash) | 7d | Refresh Token追踪 |
| `market:quote:{code}` | String (JSON) | 5s + random | 行情快照缓存 |
| `market:kline:*` | — | — | 已废弃（历史K线改为 PostgreSQL 持久化） |
| `market:throttle:{code}` | String (1) | 3s | 节流标记 |
| `trade:idempotent:{clientOrderId}` | String (1) | 300s | 下单幂等键 |
| `rate:user:{userId}:{endpoint}` | String (counter) | 60s | 接口限流计数 |
| `login:fail:{email}` | String (counter) | 1800s | 登录失败计数 |

**历史K线数据存储（2026-03-25 起）**：

| 表名 | 关键字段 | 用途 |
|---|---|---|
| `t_market_kline_daily` | `(stock_code, trade_date)` 唯一键 | 存储真实日K，按需增量 upsert |
| `t_market_kline_sync_state` | `stock_code, last_sync_date, last_bar_date` | 控制“同一股票同一天最多同步一次” |

---

## 五、前端技术栈

### 5.1 Vue 3 + Composition API

**学习目标**：掌握 Composition API、组合式函数、响应式系统。

**核心知识点**：
- `<script setup>` 语法糖
- `ref`, `reactive`, `computed`, `watch`, `watchEffect`
- `onMounted`, `onUnmounted` 生命周期
- 组合式函数（Composables）自定义复用逻辑
- `provide` / `inject` 依赖注入
- `defineProps` / `defineEmits` 类型安全通信

**推荐资源**：
| 资源 | 链接 | 重点章节 |
|---|---|---|
| Vue 3 官方文档 | https://cn.vuejs.org/guide/ | 基础、深入组件、组合式API |
| Vue 3 交互式教程 | https://cn.vuejs.org/tutorial/ | 全部15步 |
| 《Vue.js设计与实现》 | 霍春阳 著 | 理解响应式原理（可选深入） |

**Minimum Demo**：
```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getQuote } from '@/api/market'
import type { QuoteSnapshot } from '@/types/market'

const quote = ref<QuoteSnapshot | null>(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  quote.value = await getQuote('sh600519')
  loading.value = false
})
</script>
```

---

### 5.2 TypeScript

**学习目标**：类型安全编码，为 API 响应和组件 Props 定义类型。

**核心知识点**：
- 基础类型、接口(interface)、类型别名(type)
- 泛型（`Result<T>`, `PageResult<T>`）
- 联合类型 / 字面量类型（`OrderSide = 'BUY' | 'SELL'`）
- 枚举（`enum OrderStatus { ... }`）
- 与 Vue 3 集成（`defineProps<{ ... }>()`）

**推荐资源**：
| 资源 | 链接 |
|---|---|
| TypeScript 官方文档 | https://www.typescriptlang.org/docs/ |
| TypeScript 中文教程 | https://ts.xcatliu.com/ |
| Vue + TypeScript 指南 | https://cn.vuejs.org/guide/typescript/composition-api.html |

---

### 5.3 Vite 5

**学习目标**：掌握项目初始化、HMR、代理配置、构建优化。

**核心知识点**：
- `npm create vite@latest` 项目初始化
- `vite.config.ts` 配置（alias, proxy, plugins）
- 开发服务器代理（`/api` → `http://localhost:8080`）
- 环境变量（`.env`, `.env.development`）
- 构建优化（代码分割、gzip）

**推荐资源**：
| 资源 | 链接 |
|---|---|
| Vite 官方文档 | https://cn.vitejs.dev/guide/ |

---

### 5.4 Pinia

**学习目标**：替代 Vuex，掌握 Store 定义、Actions、Getters、持久化。

**核心知识点**：
- `defineStore` 定义 Store
- Setup Store 语法（推荐，与 Composition API 一致）
- Actions（异步请求封装）
- Getters（计算属性）
- `pinia-plugin-persistedstate` 持久化（Token → localStorage）
- Store 间互相引用

**推荐资源**：
| 资源 | 链接 |
|---|---|
| Pinia 官方文档 | https://pinia.vuejs.org/zh/ |
| Pinia 持久化插件 | https://prazdevs.github.io/pinia-plugin-persistedstate/zh/ |

**Minimum Demo**：
```ts
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const user = ref<UserInfo | null>(null)
  const isLoggedIn = computed(() => !!accessToken.value)

  async function login(email: string, password: string) {
    const res = await authApi.login({ email, password })
    accessToken.value = res.data.accessToken
    user.value = { id: res.data.userId, nickname: res.data.nickname }
  }

  function logout() {
    accessToken.value = null
    user.value = null
  }

  return { accessToken, user, isLoggedIn, login, logout }
})
```

---

### 5.5 ECharts 5

**学习目标**：实现 K 线图(candlestick)、折线图(收益曲线)、饼图(资产分布)。

**核心知识点**：
- `echarts.init()` + `setOption()` 基本流程
- K 线图（type: 'candlestick'）+ 成交量柱形图
- 折线图（收益曲线）
- dataZoom 组件（时间范围拖拽）
- 响应式 resize
- Vue 封装（`vue-echarts` 或自定义 composable）

**推荐资源**：
| 资源 | 链接 | 重点 |
|---|---|---|
| ECharts 官方文档 | https://echarts.apache.org/zh/index.html | 教程 + 示例 |
| ECharts 示例 | https://echarts.apache.org/examples/zh/ | Candlestick 搜索 |
| vue-echarts | https://github.com/ecomfe/vue-echarts | Vue 3 集成 |

**Minimum Demo**：
- 用静态数据渲染一个 K 线图 + MA 均线
- 添加 dataZoom 实现时间范围选择
- 实现 window.resize 自适应

---

### 5.6 Axios 封装

**学习目标**：统一请求/响应拦截、错误处理、Token 自动刷新。

**核心知识点**：
- 创建 Axios 实例（baseURL, timeout）
- 请求拦截：自动附加 `Authorization: Bearer <token>`
- 响应拦截：统一错误处理
- 401 自动刷新：使用 Refresh Token 获取新 Access Token → 重试原请求
- 避免并发刷新（Promise 队列）

**推荐资源**：
| 资源 | 链接 |
|---|---|
| Axios 官方文档 | https://axios-http.com/zh/ |
| Axios 拦截器教程 | https://www.baeldung.com/axios-interceptors |

---

## 六、WebSocket

### 6.1 STOMP over SockJS

**学习目标**：实现实时行情推送、成交通知推送。

**核心知识点**：
- Spring WebSocket 配置（`WebSocketMessageBrokerConfigurer`）
- STOMP 协议（CONNECT, SUBSCRIBE, MESSAGE, DISCONNECT）
- SockJS 兼容降级
- 按 Topic 订阅（`/topic/market/{stockCode}`, `/user/queue/notification`）
- 连接认证（握手时校验 JWT）
- 客户端：`@stomp/stompjs` + `sockjs-client`

**推荐资源**：
| 资源 | 链接 |
|---|---|
| Spring WebSocket 文档 | https://docs.spring.io/spring-framework/reference/web/websocket.html |
| STOMP.js 官方 | https://stomp-js.github.io/stomp-websocket/corehtml/index.html |
| SockJS Client | https://github.com/sockjs/sockjs-client |

**Minimum Demo**：
- 后端配置 WebSocket endpoint `/ws/market`
- 后端定时向 `/topic/market/sh600519` 发送模拟行情
- 前端订阅并实时显示价格变化

---

## 七、部署与运维

### 7.1 Docker + docker-compose

**学习目标**：容器化后端 + 前端 + 中间件，一键启动。

**核心知识点**：
- Dockerfile 编写（多阶段构建减小镜像）
- docker-compose.dev.yml 编排服务（app, pg, redis, rabbitmq, nginx）
- Volume 挂载（数据持久化）
- Network 配置（服务间通信）
- Health Check 配置
- 环境变量注入

**推荐资源**：
| 资源 | 链接 |
|---|---|
| Docker 官方文档 | https://docs.docker.com/get-started/ |
| Docker Compose 文档 | https://docs.docker.com/compose/ |
| Spring Boot Docker 指南 | https://spring.io/guides/gs/spring-boot-docker/ |

**docker-compose 服务清单**：
```yaml
services:
  app:        # Spring Boot 后端
  web:        # Nginx 静态资源 + 反向代理
  postgres:   # PostgreSQL 16
  redis:      # Redis 7
  rabbitmq:   # RabbitMQ 3.13 (含管理界面)
  prometheus: # Prometheus
  grafana:    # Grafana
```

---

### 7.2 GitHub Actions CI/CD

**学习目标**：自动化构建、测试、推送镜像、部署。

**核心知识点**：
- Workflow 语法（on/jobs/steps）
- Java 构建（`actions/setup-java` + Maven）
- 前端构建（`actions/setup-node` + pnpm）
- Docker 构建推送（`docker/build-push-action`）
- 测试报告（JaCoCo + Surefire）
- 分支策略（push main → deploy, PR → test only）

**推荐资源**：
| 资源 | 链接 |
|---|---|
| GitHub Actions 官方文档 | https://docs.github.com/en/actions |
| GitHub Actions 市场 | https://github.com/marketplace?type=actions |

---

### 7.3 监控三件套 Micrometer + Prometheus + Grafana

**学习目标**：应用指标→采集→可视化→告警全链路。

**核心知识点**：
- Micrometer：Spring Boot Actuator 自动暴露 `/actuator/prometheus`
- Prometheus：`prometheus.yml` 配置抓取目标、PromQL 查询语言
- Grafana：导入 Dashboard（Spring Boot Dashboard ID: 12900）、自定义面板、告警规则
- 自定义业务指标（Counter: 下单量, Timer: 撮合延迟, Gauge: WS连接数）

**推荐资源**：
| 资源 | 链接 |
|---|---|
| Micrometer 文档 | https://micrometer.io/docs |
| Prometheus 官方 | https://prometheus.io/docs/ |
| Grafana 官方 | https://grafana.com/docs/grafana/latest/ |
| Spring Boot Actuator | https://docs.spring.io/spring-boot/docs/3.2.x/reference/html/actuator.html |

---

## 八、测试工具

### 8.1 JUnit 5 + Mockito

**学习目标**：单元测试 + Mock 外部依赖。

| 资源 | 链接 |
|---|---|
| JUnit 5 官方文档 | https://junit.org/junit5/docs/current/user-guide/ |
| Mockito 文档 | https://site.mockito.org/ |
| Baeldung Mockito | https://www.baeldung.com/mockito-series |

### 8.2 Testcontainers

**学习目标**：集成测试使用真实 PG/Redis 容器。

| 资源 | 链接 |
|---|---|
| Testcontainers 官方 | https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/ |
| Spring Boot + TC | https://docs.spring.io/spring-boot/reference/testing/testcontainers.html |

### 8.3 REST Assured

**学习目标**：API 接口测试。

| 资源 | 链接 |
|---|---|
| REST Assured 官方 | https://rest-assured.io/ |
| Baeldung REST Assured | https://www.baeldung.com/rest-assured-tutorial |

### 8.4 k6 性能测试

**学习目标**：脚本化 API 压测。

| 资源 | 链接 |
|---|---|
| k6 官方文档 | https://k6.io/docs/ |
| k6 GitHub | https://github.com/grafana/k6 |

### 8.5 Playwright E2E

**学习目标**：浏览器端到端测试（注册→登录→下单→持仓）。

| 资源 | 链接 |
|---|---|
| Playwright 官方 | https://playwright.dev/ |
| Playwright + Vue | https://playwright.dev/docs/test-components |

---

## 九、辅助工具

### 9.1 Lombok

| 资源 | 链接 |
|---|---|
| Lombok 官方 | https://projectlombok.org/features/ |

**常用注解**：`@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Slf4j`, `@RequiredArgsConstructor`

> **注意**：Domain Entity 纯 POJO，可用 Lombok；但推荐 Record 代替 DTO/VO/Command。

### 9.2 Spotless

| 资源 | 链接 |
|---|---|
| Spotless Maven | https://github.com/diffplug/spotless/tree/main/plugin-maven |

**配置**：`mvn spotless:check`（CI检查）/ `mvn spotless:apply`（自动格式化）

---

## 十、学习检查清单

完成每项后打 ✅：

- [ ] Java 17 Record 定义 3 个 Command
- [ ] Spring Boot REST Controller + 全局异常处理
- [ ] Spring Security JWT 登录 + 受保护接口
- [ ] MyBatis-Plus CRUD + 分页 + 乐观锁
- [ ] MapStruct Entity ↔ DO ↔ VO 转换
- [ ] Flyway 创建迁移脚本并自动执行
- [ ] PostgreSQL SELECT FOR UPDATE 演示
- [ ] Redis String/Hash CRUD + TTL + Lua 限流
- [ ] RabbitMQ Producer/Consumer Demo
- [ ] WebSocket STOMP 推送 Demo
- [ ] Vue 3 + TypeScript + Vite 项目初始化
- [ ] Pinia Store 定义 + 持久化
- [ ] ECharts K 线图渲染
- [ ] Axios 封装 + 401 自动刷新
- [ ] Docker 多阶段构建后端镜像
- [ ] docker-compose 一键启动全栈
- [ ] GitHub Actions CI 流水线
- [ ] Prometheus + Grafana Dashboard
- [ ] JUnit 5 + Mockito 单元测试
- [ ] Testcontainers 集成测试
- [ ] REST Assured API 测试
