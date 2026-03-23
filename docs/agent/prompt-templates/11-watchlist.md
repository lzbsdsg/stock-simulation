# Skill Card: 自选股模块（Watchlist）

## 触发句式

> 请帮我生成自选股模块（watchlist），包含添加/移除自选股、自选股列表、实时行情推送关联。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 单用户自选股上限 | 50 只 |
| 排序 | 用户自定义排序（drag & drop） |
| 行情关联 | 自选股列表自动关联行情WebSocket推送 |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 自选股与行情推送的联动方式
2. **目录树** — `com.lzbsdsg.stocksimulation.watchlist`
3. **核心类职责**
   - `WatchlistController` — 增删改查自选股
   - `WatchlistApplicationService` — 编排逻辑
   - `WatchlistItem` — 领域实体
   - `WatchlistRepository` — 持久化接口
4. **API 契约** — GET /watchlist, POST /watchlist, DELETE /watchlist/{stockCode}, PUT /watchlist/sort
5. **Flyway SQL** — `t_watchlist_item`
6. **事务与一致性** — 幂等添加（重复添加不报错）、排序原子更新
7. **测试策略** — 添加/删除/重复添加/超限/排序

---

## 质量验收标准

- [ ] 添加幂等（重复添加返回成功不报错）
- [ ] 上限50只校验生效
- [ ] 自定义排序正确持久化
- [ ] 删除后WS推送自动解除

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 重复添加 | UNIQUE(user_id, stock_code) + INSERT ON CONFLICT DO NOTHING |
| 排序并发 | 用 sort_order 字段，批量更新 |
| 推送泄漏 | 删除自选后同步取消对应WS订阅 |
