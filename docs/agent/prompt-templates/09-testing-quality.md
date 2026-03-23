# Skill Card: 测试与质量门禁（Testing & Quality）

## 触发句式

> 请帮我为 {模块名} 生成完整的测试代码，包含单元测试、集成测试、接口测试，覆盖 Happy Path 和边界场景。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 目标模块 | 如：trade / auth / portfolio |
| 测试框架 | JUnit5 + Mockito + Testcontainers + REST Assured |
| 覆盖率目标 | domain ≥ 90%, service ≥ 80%, 总体 ≥ 70% |
| CI工具 | GitHub Actions |

---

## 输出要求（必须依次输出）

1. **Assumptions** — Testcontainers所需Docker镜像、测试数据策略
2. **测试目录树** — `src/test/java/com/lzbsdsg/stocksimulation/{module}/` 下的文件
3. **分层测试清单**

### 单元测试（domain + service）
- 每个 domain service / entity 的核心方法
- Mock 外部依赖
- 命名：`{ClassName}Test.java`
- 方法命名：`should_{expected}_when_{condition}`

### 集成测试（repository + 外部集成）
- Testcontainers 启动 PG + Redis
- 真实数据库CRUD
- 命名：`{ClassName}IntegrationTest.java`

### 接口测试（controller）
- MockMvc 或 REST Assured
- 验证 HTTP Status + Response Body + Error Code
- 至少：Happy + 401 + 400 + 业务异常
- 命名：`{ClassName}ApiTest.java`

4. **测试方法签名** — 每个核心流程至少 Happy Path + 2 个边界 Case
5. **测试配置** — `application-test.yml`, Testcontainers配置
6. **CI配置片段** — GitHub Actions workflow 中的测试步骤

---

## 质量验收标准

- [ ] 所有测试方法有明确的断言
- [ ] 测试之间无顺序依赖
- [ ] 集成测试使用 Testcontainers（不依赖本地数据库）
- [ ] 测试数据每次自动清理（@Transactional回滚 或 @DirtiesContext）
- [ ] JaCoCo 覆盖率达标

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 测试依赖顺序 | 每个测试独立Setup |
| 测试数据残留 | @Transactional 或 @Sql 清理 |
| Mock太多 | domain层尽量不Mock，测真实逻辑 |
| Testcontainers慢 | 用 @Container static 复用容器 |
| 接口测试遗漏鉴权 | 每个受保护接口测401 |
