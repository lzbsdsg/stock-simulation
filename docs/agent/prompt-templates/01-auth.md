# Skill Card: 认证模块（Auth）

## 触发句式

> 请帮我生成认证模块（auth），包含邮箱验证码注册/登录、密码登录、JWT 刷新/登出、找回密码等全部功能。

---

## 输入规范

请一并提供（若不提供则使用默认值）：

| 信息 | 默认值 |
|---|---|
| OTP 有效期 | 5 min |
| OTP 频率限制 | 同一邮箱 60s / 同一IP 20次/h |
| 密码策略 | ≥8位, 大写+小写+数字 |
| Access Token TTL | 30 min |
| Refresh Token TTL | 7 d |
| 登录失败锁定 | 5次连续失败 → 锁30min |
| BCrypt cost | 12 |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 列出所有假设（邮件服务器、Redis可用性等）
2. **目录树** — `com.lzbsdsg.stocksimulation.auth` 下的包与文件
3. **核心类职责**
   - `AuthController` — 注册/登录/刷新/登出/忘记密码 端点
   - `AuthApplicationService` — 编排OTP发送/验证、密码验证、Token签发
   - `OtpDomainService` — OTP生成、校验规则（纯逻辑，无Redis依赖）
   - `UserDomainEntity` — 密码策略校验、锁定状态判断
   - `JwtTokenProvider` — Token签发/解析/黑名单
   - `OtpRedisGateway` — Redis读写OTP
   - `EmailGateway` — 邮件发送抽象
4. **API 契约** — 至少 6 个接口 (POST /auth/register, POST /auth/login, POST /auth/login/otp, POST /auth/refresh, POST /auth/logout, POST /auth/forgot-password, POST /auth/reset-password)
5. **Flyway SQL** — `t_user`, `t_user_login_log` 建表 + 索引
6. **事务与一致性** — 注册幂等(email unique)、OTP一次性消费、Token Rotation
7. **测试策略** — 每个流程 Happy Path + 过期OTP + 频率限制 + 密码不合规 + Token过期

---

## 质量验收标准

- [ ] OTP 必须一次性消费（验证后删除Redis key）
- [ ] 密码不以明文出现在日志/响应/数据库
- [ ] Refresh Token Rotation 生效
- [ ] 连续失败锁定生效
- [ ] 所有接口限流生效
- [ ] 单元测试覆盖率 ≥ 80%

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| OTP被重放 | 验证即删除Redis key |
| 邮件轰炸 | 60s频率限制 + IP限制 + 图形验证码触发 |
| JWT被盗 | Access短过期 + Refresh Rotation + 登出黑名单 |
| 注册重复提交 | email UNIQUE + 接口幂等 |
| 密码枚举 | 统一返回"凭据错误"，不区分"用户不存在"和"密码错误" |
