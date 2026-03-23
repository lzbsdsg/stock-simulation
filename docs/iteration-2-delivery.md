# Iteration 2 交付说明（认证模块 Auth）

## 1. 迭代目标

根据路线图与认证模板要求，完成 Iteration 2 的认证模块交付，覆盖以下能力：

- 邮箱 OTP 发送与验证（一次性消费）
- 注册、密码登录、OTP 登录
- JWT 签发、刷新（Refresh Rotation）、登出黑名单
- 忘记密码与重置密码
- 安全过滤链与受保护接口鉴权
- OTP 发送限流（注解限流 + Redis 邮箱/IP 双限流）

## 2. 完成任务清单

### 2.1 领域与应用编排

- 完成 `AuthApplicationService` 核心流程编排：
  - `sendOtp`、`register`、`login`、`loginByOtp`
  - `refreshToken`（旧 Refresh 失效）
  - `logout`（Access Token 黑名单）
  - `resetPassword`
- 完成 `AccountApplicationService#createAccount`，在注册流程中创建账户。

### 2.2 基础设施网关

- 完成 `OtpRedisGateway`：
  - OTP 存储与读取（TTL=5 分钟）
  - OTP 校验后删除（一次性消费）
  - 邮箱 60 秒频率限制（SETNX + TTL）
  - IP 每小时 20 次限制
- 完成 `JwtTokenProvider`：
  - Access/Refresh 生成与校验
  - Refresh Token 存储、校验与轮换
  - Token 黑名单写入与校验
- 完成 `EmailGateway`：
  - 通过 MQ 发送邮件消息体到邮件交换机。
- 完成 `EmailSendConsumer`：
  - 消费 `email.send.queue` 并通过 SMTP 实际发送邮件。
  - 未配置 `spring.mail.username` 时仅记录告警并跳过发送，保证开发环境可启动。

### 2.3 安全与接口

- 完成 `SecurityConfig`：`/api/v1/auth/**` 放行，其余接口鉴权。
- 完成 `JwtAuthenticationFilter`：Bearer Token 解析、校验并注入 `SecurityContext`。
- 完成 `AuthController` 认证接口（8 个端点）：
  - `/otp/send`
  - `/register`
  - `/login`
  - `/login/otp`
  - `/refresh`
  - `/logout`
  - `/forgot-password`
  - `/reset-password`
- 在 OTP 发送端点增加 `@RateLimit`，配合 Redis 限制形成双重保护。

## 3. 测试清单与命令

### 3.1 本次迭代测试文件

- `src/test/java/com/lzbsdsg/stocksimulation/auth/domain/service/OtpDomainServiceTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/auth/domain/service/PasswordDomainServiceTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/auth/application/AuthApplicationServiceTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/auth/controller/AuthControllerApiTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/auth/infrastructure/gateway/OtpRedisGatewayIntegrationTest.java`

### 3.2 执行命令

在项目根目录执行：

```bat
cd /d d:\StockSimulation\stock-simulation
call .\mvnw.cmd -Dtest=OtpDomainServiceTest,PasswordDomainServiceTest,AuthApplicationServiceTest,AuthControllerApiTest,OtpRedisGatewayIntegrationTest test
```

### 3.3 实际执行结果

- 通过测试数：35
- 失败测试数：0

### 3.4 接口验收测试（Swagger）

除自动化测试外，需在 Swagger 中完成接口验收，确认 HTTP 契约与真实链路行为。

- Swagger 入口：`/swagger-ui/index.html`
- 验收接口：
  - `POST /api/v1/auth/otp/send`
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/login/otp`
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`
  - `POST /api/v1/auth/forgot-password`
  - `POST /api/v1/auth/reset-password`
- 重点检查：
  - OTP 发送后可在邮箱收到 6 位验证码（已配置 `MAIL_USERNAME/MAIL_PASSWORD`）
  - 注册成功返回 `accessToken` 与 `refreshToken`
  - `refresh` 后旧 Refresh Token 失效（Rotation 生效）
  - 无 Token 访问受保护接口返回 401
  - OTP 频控触发时返回 429，响应头包含 `X-RateLimit-*`

### 3.5 接口验收用例（可直接在 Swagger 执行）

说明：

- `TEST_EMAIL` 替换为测试邮箱
- `OTP_CODE` 替换为邮箱收到的 6 位验证码
- `ACCESS_TOKEN`/`REFRESH_TOKEN` 使用上一步接口返回值

1. 发送验证码

```http
POST /api/v1/auth/otp/send
Content-Type: application/json

{
  "email": "TEST_EMAIL"
}
```

预期：HTTP 200，邮箱收到验证码。

1. 验证码注册

```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "email": "TEST_EMAIL",
  "otp": "OTP_CODE",
  "password": "Strong123",
  "nickname": "tester",
  "initialBalance": 10000
}
```

预期：HTTP 200，返回 `accessToken` 与 `refreshToken`。

1. 密码登录

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "TEST_EMAIL",
  "password": "Strong123"
}
```

预期：HTTP 200，返回新 token 对。

1. 验证码登录

```http
POST /api/v1/auth/login/otp
Content-Type: application/json

{
  "email": "TEST_EMAIL",
  "otp": "OTP_CODE"
}
```

预期：HTTP 200，返回新 token 对。

1. 刷新 Token

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "REFRESH_TOKEN"
}
```

预期：HTTP 200，返回新 token 对。

1. 使用旧 Refresh Token 再刷新（Rotation 验证）

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "旧的REFRESH_TOKEN"
}
```

预期：认证失败（旧 token 已失效）。

1. 登出

```http
POST /api/v1/auth/logout
Authorization: Bearer ACCESS_TOKEN
```

预期：HTTP 200，Access Token 加入黑名单。

1. 忘记密码 + 重置密码

```http
POST /api/v1/auth/forgot-password
Content-Type: application/json

{
  "email": "TEST_EMAIL"
}
```

```http
POST /api/v1/auth/reset-password
Content-Type: application/json

{
  "email": "TEST_EMAIL",
  "otp": "OTP_CODE",
  "newPassword": "NewStrong123"
}
```

预期：HTTP 200，新密码可登录，旧密码不可登录。

1. 负例用例（必测）

- 连续快速调用 `POST /api/v1/auth/otp/send`：预期 HTTP 429，含 `X-RateLimit-*` 头
- `register` 使用非法邮箱：预期 HTTP 400
- 受保护接口不带 token：预期 HTTP 401

## 4. 通过标准（Iteration 2）

满足以下标准视为通过：

- OTP 可发送、校验并一次性消费（验证后删除）
- 当配置 `MAIL_USERNAME/MAIL_PASSWORD` 后，OTP 可送达真实邮箱
- 注册/登录/OTP 登录可返回 Token
- Refresh Token 轮换生效（旧 Refresh 失效）
- 登出后 Token 进入黑名单
- 认证放行与受保护接口鉴权策略生效
- OTP 发送具备注解限流 + Redis 邮箱/IP 双限流
- 迭代 2 核心测试全部通过（35/35）

## 5. 备注

- 本交付文档聚焦 Iteration 2（Auth 模块）能力闭环与测试结果。
- SMTP 实发依赖环境变量：`MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、`MAIL_PASSWORD`。
- 其他迭代（Trade/Market/Portfolio 等）不在本文验收范围内。
