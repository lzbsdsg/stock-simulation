# Iteration 1 交付说明（通用基础设施）

## 1. 迭代目标

根据 docs 开发路线图与详细设计文档，完成 Iteration 1 的通用基础设施交付，覆盖以下能力：

- 多级缓存抽象（L1 Caffeine + L2 Redis）
- 限流注解与 AOP（Redis + Lua 令牌桶）
- 读写分离路由（主从数据源 + AOP 上下文）
- 异步线程池配置
- TraceId 工具与 MDC 过滤器
- 迭代 1 指定测试补齐与执行

## 2. 完成任务清单

### 2.1 多级缓存

- 新增/完善：
  - common/cache/MultiLevelCacheManager
  - common/cache/CacheInvalidateListener
  - config/CaffeineConfig
  - config/RedisConfig（增加 Pub/Sub 监听容器）
- 交付能力：
  - 统一 `get/put/evict` 缓存编排
  - 读链路：L1 -> L2 -> 回源
  - L2 命中自动回填 L1
  - 删除时发布 `cache:invalidate:{region}`，多实例可清理本地 L1
  - 在 Web 请求线程中写入 `X-Cache-Status: HIT-L1/HIT-L2/MISS`

### 2.2 限流组件

- 新增/完善：
  - common/annotation/RateLimit
  - common/aspect/RateLimitAspect
  - resources/lua/rate_limit.lua
- 交付能力：
  - 支持注解参数：`limit/window/key` 与 `maxRequests/timeWindow/timeUnit/keyPrefix` 兼容
  - 基于 Redis + Lua 令牌桶进行放行判断
  - 超限抛出 BizException（映射 429）
  - 返回限流头：
    - `X-RateLimit-Limit`
    - `X-RateLimit-Remaining`
    - `X-RateLimit-Reset`

### 2.3 读写分离

- 新增/完善：
  - common/util/DataSourceContextHolder
  - common/aspect/DataSourceAspect
  - config/DataSourceRoutingConfig
- 交付能力：
  - 默认主库路由
  - `@ReadOnly` 方法走从库
  - `@Transactional` 方法强制主库
  - 主从 Hikari 参数按文档配置（master 10/30，slave 20/50）

### 2.4 异步配置与链路追踪

- 新增/完善：
  - config/AsyncConfig
  - common/util/TraceIdUtil
  - common/filter/TraceIdFilter
- 交付能力：
  - 异步线程池参数：core=8, max=32, queue=500
  - 每个请求自动注入/透传 `X-Trace-Id`，写入 MDC 并在请求结束后清理

## 3. 测试清单与命令

### 3.1 新增测试文件

- src/test/java/com/lzbsdsg/stocksimulation/common/cache/MultiLevelCacheManagerTest.java
- src/test/java/com/lzbsdsg/stocksimulation/common/aspect/RateLimitAspectTest.java
- src/test/java/com/lzbsdsg/stocksimulation/common/aspect/DataSourceRoutingTest.java

### 3.2 执行命令

在项目根目录执行：

```bat
cd /d d:\StockSimulation\stock-simulation
call .\mvnw.cmd -Dtest=MultiLevelCacheManagerTest,RateLimitAspectTest,DataSourceRoutingTest test
```

或在 VS Code 测试资源管理器执行以上 3 个测试类。

### 3.3 实际执行结果

- 通过测试数：14
- 失败测试数：0

## 4. 通过标准（Iteration 1）

满足以下标准视为通过：

- `@RateLimit` 生效，超限返回业务错误并带 `X-RateLimit-*` 头
- 多级缓存支持 L1/L2 命中及回源写回
- Redis Pub/Sub 失效消息可触发本地 L1 清理
- `@ReadOnly` 与 `@Transactional` 路由策略可用
- 新增迭代 1 基础设施测试全部通过（14/14）

## 5. 备注

- 本次交付聚焦 Iteration 1（通用基础设施），未在本交付文件中覆盖后续业务迭代（Auth/Trade/Market 等）的完整验收。
