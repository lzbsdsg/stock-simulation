# Iteration 3 交付说明（用户与账户模块 User/Account）

## 1. 迭代目标

基于路线图与详细设计文档，完成 Iteration 3 用户与账户模块交付，覆盖以下能力：

- 注册后自动创建资金账户
- 头像上传与资料头像修改
- 账户冻结/解冻、扣减冻结、入账能力
- 账户并发安全（同账户并发冲突控制）
- 乐观锁冲突重试与失败抛错
- 用户信息查询、资料修改、密码修改接口

## 2. 完成任务清单

### 2.1 领域与应用服务

- 完成 `AccountApplicationService`：
  - `createAccount`：初始化账户（初始资金范围校验）
  - `freezeBalance`：冻结可用余额
  - `unfreezeBalance`：解冻冻结余额
  - `deductFrozen`：成交扣减冻结资金并处理差额
  - `creditBalance`：卖出入账
  - 统一金额正数校验与余额一致性校验
  - 乐观锁重试（最多 3 次），超过次数抛 `OptimisticLockingFailureException`

- 完成 `UserApplicationService`：
  - `getCurrentUser`：查询当前用户资料
  - `updateProfile`：修改昵称、头像
  - `uploadAvatar`：上传头像文件并更新用户头像 URL
  - `changePassword`：校验旧密码与新密码强度后更新

### 2.2 控制器接口

- 完成 `UserController`：
  - `GET /api/v1/user/me`：获取当前用户信息
  - `PUT /api/v1/user/me`：修改当前用户资料
  - `POST /api/v1/user/avatar`：上传并更新头像
  - `PUT /api/v1/user/password`：修改密码
- 从 `SecurityContext` 读取当前用户 ID，未认证时返回 401 语义错误。

### 2.3 头像上传与存储

- 新增本地头像存储服务：`AvatarStorageService`
  - 存储目录：`${avatar.storage.root-dir}/avatars/{yyyyMM}/`
  - 访问前缀：`${avatar.storage.public-prefix}`（默认 `/uploads`）
  - 默认大小限制：2MB（`avatar.storage.max-size-bytes`）
  - 支持格式：jpg/jpeg/png/webp/gif
- 新增静态资源映射：`/uploads/**` → 本地 `uploads/` 目录
- 安全放行：`/uploads/**`

### 2.4 并发与锁策略落地

- 仓储层保留行级锁查询：`SELECT ... FOR UPDATE`（`AccountMapper#selectByUserIdForUpdate`）
- 更新路径通过 `@Version` + MyBatis-Plus 乐观锁插件实现 CAS 更新
- 应用层在冲突时执行有限重试，避免瞬时冲突导致直接失败

## 3. 测试清单与命令

### 3.1 本次迭代新增/完善测试文件

- `src/test/java/com/lzbsdsg/stocksimulation/user/domain/service/AccountDomainServiceTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/user/infrastructure/AccountRepositoryIntegrationTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/performance/TradeConcurrencyTest.java`
- `src/test/java/com/lzbsdsg/stocksimulation/user/controller/UserControllerApiTest.java`

当前覆盖说明：

- 自动化测试已覆盖账户核心规则、仓储行为、并发锁策略，以及头像上传成功路径。
- 头像上传负向场景（非法格式、超大小）在 `docs/iteration-3-acceptance-script.md` 中通过 CMD 脚本执行验收。

### 3.2 建议执行命令

在项目根目录执行：

```bat
cd /d d:\StockSimulation\stock-simulation
call .\mvnw.cmd -Dtest=AccountDomainServiceTest,AccountRepositoryIntegrationTest,TradeConcurrencyTest,UserControllerApiTest test
```

### 3.3 本次实际执行结果

- 通过测试数：24
- 失败测试数：0

## 4. 接口验收（不仅限于测试类通过）

### 4.1 Swagger 验收入口

- `http://localhost/swagger-ui/index.html`

### 4.2 用户模块接口验收用例

1. 获取当前用户信息

```http
GET /api/v1/user/me
Authorization: Bearer ACCESS_TOKEN
```

预期：HTTP 200，返回 `userId/email/nickname/avatarUrl/role/status`。

2. 修改用户资料

```http
PUT /api/v1/user/me
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{
  "nickname": "newNick",
  "avatarUrl": "https://example.com/avatar.png"
}
```

预期：HTTP 200，返回更新后的 `nickname/avatarUrl`。

3. 上传头像

```http
POST /api/v1/user/avatar
Authorization: Bearer ACCESS_TOKEN
Content-Type: multipart/form-data

file=<avatar.png>
```

预期：HTTP 200，返回更新后的 `avatarUrl`（例如 `/uploads/avatars/202603/u1-xxxx.png`），
并且可直接 GET 访问该 URL。

4. 修改密码

```http
PUT /api/v1/user/password
Authorization: Bearer ACCESS_TOKEN
Content-Type: application/json

{
  "oldPassword": "Strong123",
  "newPassword": "NewStrong123"
}
```

预期：HTTP 200；之后旧密码登录失败，新密码登录成功。

### 4.3 账户能力验收（注册与并发）

1. 注册自动创建账户（依赖迭代2注册接口）

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

验收 SQL：

```sql
SELECT user_id, initial_balance, available_balance, frozen_balance, version
FROM t_user_account
WHERE user_id = <注册返回的userId>;
```

预期：存在对应记录，`available_balance=initial_balance`，`frozen_balance=0`。

2. 并发冻结安全性（同账户）

- 执行 `TradeConcurrencyTest` 中同账户并发冻结场景。
- 预期：余额不足场景下仅 1 个请求成功，不出现超卖。

3. 乐观锁冲突重试

- 执行 `TradeConcurrencyTest` 乐观锁冲突场景。
- 预期：瞬时冲突可重试成功；持续冲突抛 `OptimisticLockingFailureException`。

## 5. Iteration 3 通过标准

满足以下全部条件视为通过：

- 账户初始化、冻结/解冻、扣减冻结、入账能力已实现并通过测试
- 同账户并发冻结场景无超卖，不同账户场景可并行处理
- 乐观锁冲突具备重试机制，超过重试上限给出明确异常
- 用户模块 4 个接口可通过 Swagger 正常调用
- 用户模块头像上传接口可正常上传并返回可访问 URL
- 头像上传负向场景（非法格式、超大小）可按验收脚本返回 HTTP 400
- 修改密码后旧密码失效、新密码生效
- 注册后数据库中可查询到对应账户记录
- 本次迭代测试集合全部通过（24/24）

## 6. 备注

- 账户冻结/解冻的业务校验在领域服务层完成；事务与重试编排在应用服务层完成。
- 接口验证时需先获取有效 JWT（可通过认证模块登录接口获取）。
- 若要执行真实数据库验收，请确保本地 PostgreSQL 与 Redis/RabbitMQ 测试配置可用。
