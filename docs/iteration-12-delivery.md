# Iteration 12 交付说明（WebSocket完善 + 自选股 + 消息通知）

## 1. 迭代目标

基于 docs/doc-D-dev-roadmap.md 的 Iteration 12（Week 13）：

- 完成自选股管理（增删改序）
- 完成消息通知中心（未读、已读）
- 完成用户私有 WebSocket 通知链路
- 打通成交事件到站内消息和前端 Toast

## 2. 本次完成项

### 2.1 后端

- 完成自选股应用服务实现：
  - src/main/java/com/lzbsdsg/stocksimulation/watchlist/application/WatchlistApplicationService.java
  - 能力：查询、添加、删除、排序、50只上限、重复校验
- 新增自选股应用服务单测：
  - src/test/java/com/lzbsdsg/stocksimulation/watchlist/application/WatchlistApplicationServiceTest.java
- 通知链路确认：
  - TradePushConsumer 消费成交事件
  - NotificationApplicationService 落库并推送 /user/queue/notification

### 2.2 前端

- 新增自选股页面与 store：
  - stock-simulation-web/src/pages/watchlist/WatchlistPage.vue
  - stock-simulation-web/src/stores/watchlist.ts
  - stock-simulation-web/src/api/watchlist.ts
  - stock-simulation-web/src/types/watchlist.ts
- 新增通知中心与私有 WS：
  - stock-simulation-web/src/components/notification/NotificationBell.vue
  - stock-simulation-web/src/stores/notification.ts
  - stock-simulation-web/src/api/notification.ts
  - stock-simulation-web/src/composables/useNotificationSocket.ts
  - stock-simulation-web/src/types/notification.ts
- 布局接入：
  - 右上角消息铃铛
  - /watchlist 导航
  - 默认布局加载后同步自选股订阅
  - stock-simulation-web/src/layouts/DefaultLayout.vue

### 2.3 Flyway 与路线图状态

- Flyway 文件已存在并可用：
  - src/main/resources/db/migration/V20260213_006__create_watchlist_table.sql
  - src/main/resources/db/migration/V20260213_007__create_notification_table.sql
- Iteration 12 路线图任务状态已回填：
  - docs/doc-D-dev-roadmap.md

## 3. 核心接口验收点

- GET /api/v1/watchlist
- POST /api/v1/watchlist/{stockCode}
- DELETE /api/v1/watchlist/{stockCode}
- PUT /api/v1/watchlist/sort
- GET /api/v1/notifications
- PUT /api/v1/notifications/{id}/read
- PUT /api/v1/notifications/read-all
- GET /api/v1/notifications/unread-count
- WS: /user/queue/notification

## 4. 已执行命令与结果

### 4.1 后端测试（已执行）

```cmd
call .\mvnw.cmd -Dtest=WatchlistApplicationServiceTest test
```

结果：通过（含在本次核心用例集合中，0 失败）。

### 4.2 前端检查（已执行）

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm lint
pnpm test
pnpm build
```

结果：
- lint 通过
- test 通过（含 watchlist store 用例）
- build 通过

## 5. 功能验收步骤

1. 启动后端与前端：

```cmd
cd /d d:\StockSimulation\stock-simulation
call .\mvnw.cmd spring-boot:run
```

```cmd
cd /d d:\StockSimulation\stock-simulation\stock-simulation-web
pnpm dev
```

2. 自选股验收：
- 打开 /watchlist
- 添加/删除股票
- 拖拽排序后刷新，顺序保持
- 超过 50 只应返回业务错误

3. 通知验收：
- 完成一笔可成交交易
- 校验右上角铃铛未读数变化
- 下拉可见通知并支持单条已读/全部已读
- 页面出现成交 Toast

## 6. 通过标准

- 自选股增删改序可用
- 最大 50 只限制生效
- 成交后通知入库并通过私有 WS 推送
- 前端通知铃铛、未读计数、已读操作可用
- 前端构建与测试通过
