# Iteration 3 验收脚本文档（User/Account，CMD 版）

本文档用于可执行验收，覆盖：

- 接口可用性
- 注册后自动建账
- 用户资料/密码修改可用
- 账户并发冻结安全
- 测试命令与通过标准

## 1. 前置条件

- 已启动 PostgreSQL / Redis / RabbitMQ
- 后端服务已启动（默认 http://localhost:8080）
- 已具备一个可收验证码的邮箱

建议先执行：

```bat
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

通过标准：

- 控制台出现 Started StockSimulationApplication
- 可访问 http://localhost:8080/swagger-ui/index.html

## 2. 变量准备（CMD）

新开一个 cmd 窗口，执行：

```bat
cd /d d:\StockSimulation\stock-simulation
set BASE=http://localhost:8080
set EMAIL=请替换为你的测试邮箱@example.com
set PASSWORD_OLD=Strong123
set PASSWORD_NEW=NewStrong123
set NICKNAME=iter3_tester
set INITIAL_BALANCE=10000
```

## 3. OTP 发送与注册

### 3.1 发送 OTP

```bat
curl -i -X POST "%BASE%/api/v1/auth/otp/send" ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"%EMAIL%\"}"
```

通过标准：

- HTTP 200
- 邮箱收到 6 位验证码

### 3.2 注册（会自动创建账户）

手工输入邮箱收到的 OTP：

```bat
set /p OTP=请输入邮箱收到的6位验证码:
curl -s -X POST "%BASE%/api/v1/auth/register" ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"%EMAIL%\",\"otp\":\"%OTP%\",\"password\":\"%PASSWORD_OLD%\",\"nickname\":\"%NICKNAME%\",\"initialBalance\":%INITIAL_BALANCE%}" > register-response.json

type register-response.json
```

从返回 JSON 手工复制：

- accessToken 到 ACCESS_TOKEN
- userId 到 USER_ID

```bat
set /p ACCESS_TOKEN=请粘贴 accessToken:
set /p USER_ID=请粘贴 userId:
```

通过标准：

- HTTP 200
- 返回 accessToken、refreshToken、userId

## 4. 注册后自动建账 SQL 验收

使用数据库客户端执行：

```sql
SELECT user_id, initial_balance, available_balance, frozen_balance, version
FROM t_user_account
WHERE user_id = <USER_ID>;
```

通过标准：

- 查询有且仅有 1 条记录
- available_balance = initial_balance
- frozen_balance = 0

## 5. 用户接口验收（迭代3核心）

### 5.1 获取当前用户

```bat
curl -i -X GET "%BASE%/api/v1/user/me" ^
  -H "Authorization: Bearer %ACCESS_TOKEN%"
```

通过标准：

- HTTP 200
- 返回 userId/email/nickname/avatarUrl/role/status
- userId 与注册返回一致

### 5.2 修改资料

```bat
curl -i -X PUT "%BASE%/api/v1/user/me" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %ACCESS_TOKEN%" ^
  -d "{\"nickname\":\"iter3_updated\",\"avatarUrl\":\"https://example.com/avatar.png\"}"
```

通过标准：

- HTTP 200
- 返回中的 nickname = iter3_updated
- 再次调用 /api/v1/user/me 能看到更新值

### 5.3 修改密码

```bat
curl -i -X PUT "%BASE%/api/v1/user/password" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %ACCESS_TOKEN%" ^
  -d "{\"oldPassword\":\"%PASSWORD_OLD%\",\"newPassword\":\"%PASSWORD_NEW%\"}"
```

通过标准：

- HTTP 200

## 6. 改密生效验收（旧密码失效，新密码生效）

### 6.1 旧密码登录（应失败）

```bat
curl -i -X POST "%BASE%/api/v1/auth/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"%EMAIL%\",\"password\":\"%PASSWORD_OLD%\"}"
```

通过标准：

- 非 200（通常 401）

### 6.2 新密码登录（应成功）

```bat
curl -i -X POST "%BASE%/api/v1/auth/login" ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"%EMAIL%\",\"password\":\"%PASSWORD_NEW%\"}"
```

通过标准：

- HTTP 200
- 返回新 token

## 7. 并发与锁策略验收（自动化）

在项目根目录执行：

```bat
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=TradeConcurrencyTest" test
```

通过标准：

- Tests run 全通过
- 同账户并发冻结场景：余额不足时仅一个成功，不发生超卖
- 乐观锁冲突场景：可重试成功，持续冲突抛明确异常

## 8. 迭代3测试命令（完整建议）

```bat
cd /d d:\StockSimulation\stock-simulation
mvnw.cmd "-Dtest=AccountDomainServiceTest,AccountRepositoryIntegrationTest,TradeConcurrencyTest,UserControllerApiTest" test
```

通过标准：

- 目标测试全部通过（当前基线：23 通过，0 失败）

## 9. 一票否决项

任一项失败即判定未通过：

- 注册成功但 t_user_account 无记录
- /api/v1/user/me、/api/v1/user/password 任一不可用
- 改密后旧密码仍可登录
- 并发冻结出现超卖或资金不一致
- 目标测试集存在失败
