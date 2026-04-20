# Skill Card: 全局配置与基础设施（Config & Infrastructure）

## 触发句式

> 请帮我生成全局配置模块（config/common），包含统一响应格式、全局异常处理、错误码体系、限流配置、跨域配置。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 统一响应 | `Result<T>{ code, message, data, traceId, timestamp }` |
| 成功码 | `"000000"` |
| 错误码格式 | `MODULE_SCENE_REASON` |
| 限流方式 | Redis + Lua 令牌桶 |
| 跨域 | 开发环境允许 localhost:5173 |

---

## 输出要求（必须依次输出）

1. **Assumptions** — Spring Boot配置profile（dev/test）
2. **目录树** — `com.lzbsdsg.stocksimulation.common` + `com.lzbsdsg.stocksimulation.config`
3. **核心类职责**
   - `Result<T>` — 统一响应包装
   - `ErrorCode` — 错误码枚举
   - `BizException` — 业务异常
   - `GlobalExceptionHandler` — @RestControllerAdvice
   - `RateLimitAspect` — @RateLimit 注解 + AOP 切面
   - `SecurityConfig` — Spring Security 配置
   - `WebSocketConfig` — STOMP 配置
   - `RedisConfig` — 序列化 + 连接池
   - `CorsConfig` — 跨域
   - `SwaggerConfig` — OpenAPI文档
   - `TradeRuleConfig` — 交易规则可配置Bean
4. **API 契约** — 不适用（纯基础设施）
5. **Flyway SQL** — `t_sys_config` 系统配置表（可选）
6. **事务与一致性** — 不适用
7. **测试策略** — 异常处理覆盖(各种BizException), 限流AOP测试, CORS测试

---

## 质量验收标准

- [ ] 所有接口返回统一 Result 格式
- [ ] 未捕获异常不泄漏堆栈到前端
- [ ] 限流注解可应用于任意Controller方法
- [ ] 错误码可被前端国际化映射
- [ ] Profile切换（dev/test）不影响功能

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 异常堆栈泄漏 | GlobalExceptionHandler中非BizException返回通用错误 |
| 限流key碰撞 | key = userId + method + path |
| Redis序列化 | 统一用 Jackson2JsonRedisSerializer |
| Profile混淆 | 敏感配置外部化（环境变量 / Vault） |
