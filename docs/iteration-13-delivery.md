# Iteration 13 交付说明（管理员模块 + 实时性能面板）

## 1. 迭代目标

基于 docs/doc-D-dev-roadmap.md 的 Iteration 13（Week 14）：

- 建立管理员权限闭环（仅 ADMIN 可访问管理接口与前端页面）
- 补齐管理后台后端基础能力（用户列表、状态切换、统计、排行榜）
- 新增管理员前端入口与路由守卫
- 将行情链路实时观测指标接入管理员前端面板

## 2. 本次完成项

### 2.1 后端

- 管理接口权限收敛：
  - `SecurityConfig` 增加 `/api/v1/admin/**` 的 `hasRole("ADMIN")`
- JWT 角色注入与 Access Token 严格校验：
  - `JwtTokenProvider` 新增角色解析与 access token 判定
  - `JwtAuthenticationFilter` 注入 `ROLE_*` 到 `SecurityContext`
  - `WebSocketStompInterceptor` 仅接受 access token
- 管理后台应用服务实现：
  - `AdminApplicationService` 完成 `listUsers`、`toggleUserStatus`、`getDashboardStats`、`getLeaderboard`

### 2.2 前端

- 鉴权会话补齐角色信息：
  - `types/auth.ts`、`stores/auth.ts`、`utils/auth-storage.ts`、`api/request.ts`
  - 增加 `role` 持久化与 `isAdmin` 计算属性
- 路由与入口：
  - `router/index.ts` 新增 `/admin` 路由（`requiresAdmin`）
  - `DefaultLayout.vue` 仅管理员显示“管理后台”导航
- 管理员页面：
  - 新增 `pages/admin/AdminConsolePage.vue`
  - 新增 `api/admin.ts`、`types/admin.ts`
  - 接入 `GET /api/v1/admin/dashboard/stats`
  - 接入 `GET /api/v1/market/realtime-metrics`

### 2.3 质量与测试

- 新增后端安全测试：
  - `src/test/java/com/lzbsdsg/stocksimulation/admin/controller/AdminControllerSecurityTest.java`
  - 校验：未登录拒绝、非管理员拒绝、管理员放行
- 前端会话存储兼容性增强：
  - `auth-storage.ts` 增加测试环境内存回退，保障无完整 localStorage 时测试稳定

## 3. 核心接口验收点

- `GET /api/v1/admin/dashboard/stats`（仅 ADMIN）
- `GET /api/v1/admin/users?page=1&size=20`（仅 ADMIN）
- `PUT /api/v1/admin/users/{userId}/status?status=ACTIVE|DISABLED|LOCKED`（仅 ADMIN）
- `GET /api/v1/admin/leaderboard?page=1&size=20`（仅 ADMIN）
- `GET /api/v1/market/realtime-metrics`（管理员面板展示）

## 4. 已执行命令与结果

### 4.1 后端测试（已执行）

```cmd
call .\mvnw.cmd -Dtest=AdminControllerSecurityTest test
```

结果：通过（3 条用例，0 失败）。

### 4.2 后端编译（已执行）

```cmd
call .\mvnw.cmd -DskipTests compile
```

结果：通过。

### 4.3 前端检查（已执行）

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm vitest run src/stores/__tests__/auth.spec.ts
pnpm build
```

结果：
- auth store 单测通过
- build 通过

## 5. 功能验收步骤

1. 使用普通用户登录，确认：
- 侧边栏不显示“管理后台”
- 直接访问 `/admin` 被路由守卫拦截回 `/dashboard`

1. 使用管理员用户登录，确认：
- 侧边栏显示“管理后台”
- 访问 `/admin` 正常打开管理员控制台

1. 管理员面板验收：
- “系统概览”卡片可显示用户/交易/金额统计
- “实时性能面板”可显示 WS 连接、队列、降级状态、延迟分位
- 自动刷新（3 秒）与“立即刷新”按钮均生效

1. 后端权限验收：
- 普通用户调用 `/api/v1/admin/**` 返回 403
- 管理员调用 `/api/v1/admin/**` 返回 200

## 6. 通过标准

- 管理员能力有独立迭代交付文档（Iteration 13）
- 管理员接口仅 ADMIN 可访问
- 管理员前端页面仅 ADMIN 可进入
- 管理员实时性能面板可稳定展示实时观测数据
- 后端测试与前端构建通过
