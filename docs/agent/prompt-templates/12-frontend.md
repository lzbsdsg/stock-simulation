# Skill Card: 前端架构与页面（Frontend）

## 触发句式

> 请帮我生成前端 {页面/组件/Store} 的完整代码，使用 Vue 3 + TypeScript + Pinia + ECharts。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 框架 | Vue 3.4+ (Composition API) + TypeScript |
| 构建 | Vite 5+ |
| UI库 | Element Plus (或 Naive UI，按需) |
| 状态管理 | Pinia |
| 图表 | ECharts 5 |
| HTTP | Axios |
| 路由 | Vue Router 4 |
| 包管理 | pnpm |
| 代码规范 | ESLint + Prettier |

---

## 输出要求（必须依次输出）

1. **Assumptions** — UI框架选择、响应式要求
2. **目录树** — `stock-simulation-web/src/` 下的完整结构
3. **核心文件**
   - `api/` — 按模块划分 API 封装（auth.ts, market.ts, trade.ts 等）
   - `api/request.ts` — Axios 拦截器（401自动refresh + 重试）
   - `stores/` — Pinia Store（useAuthStore, useMarketStore, useTradeStore, usePortfolioStore）
   - `composables/` — 可复用逻辑（useWebSocket, useCountdown 等）
   - `pages/` — 页面组件
   - `components/` — 通用组件（KLineChart, OrderForm, PositionTable 等）
   - `types/` — TypeScript 类型定义
4. **路由设计** — 路由表 + 路由守卫（登录态检查）
5. **WebSocket方案** — 连接管理 + 自动重连 + 消息分发
6. **测试策略** — Vitest 单测（Store + Utils）+ Playwright E2E

---

## 质量验收标准

- [ ] TypeScript 严格模式，无 any
- [ ] 401 自动刷新 + 原请求重试
- [ ] WebSocket 断线自动重连
- [ ] ECharts K线图正确渲染
- [ ] 路由守卫生效（未登录跳转登录页）

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| Token刷新竞态 | 用Promise队列，刷新期间其他请求等待 |
| WebSocket对象泄漏 | 组件unmount时关闭连接 |
| ECharts resize | 监听window.resize + ResizeObserver |
| 路由闪烁 | 登录态判断放在路由守卫，非组件内 |
| 类型不安全 | 后端返回数据用 zod/yup 运行时校验 |
