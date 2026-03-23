# Skill Card: 模拟交易核心（Trade Core）

## 触发句式

> 请帮我生成交易模块（trade），包含委托下单、撮合成交、撤单、资金冻结/解冻、手续费计算、T+1规则。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 最小交易单位 | 100 股（1手） |
| 涨跌停 | ±10%（ST股±5%，科创板/创业板±20%） |
| 手续费 | 佣金万三(最低5元) + 印花税千一(卖出) + 过户费十万分之一 |
| T+1 | 买入当日不可卖出 |
| 交易时间 | 9:30-11:30, 13:00-15:00（可配置） |
| 幂等键 | clientOrderId (UUID) |
| 乐观锁重试 | 最多 3 次，50ms 指数退避 |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 撮合模式(即时/定时)、部分成交策略、集合竞价简化
2. **目录树** — `com.lzbsdsg.stocksimulation.trade` 下的包与文件
3. **核心类职责**
   - `OrderController` — 下单/撤单/查询端点
   - `TradeApplicationService` — 编排下单→冻结→入库、撤单→解冻
   - `OrderDomainService` — 订单校验规则（时间/价格/数量/涨跌停）
   - `MatchEngine` — 撮合逻辑（模拟成交判定）
   - `FeeCalculator` — 手续费/印花税/过户费计算
   - `Order` / `Trade` — 领域实体
   - `OrderRepository` (interface) — 订单持久化抽象
   - `TradeRepository` (interface) — 成交持久化抽象
4. **API 契约** — POST /trade/order, DELETE /trade/order/{id}, GET /trade/orders, GET /trade/trades
5. **Flyway SQL** — `t_trade_order`, `t_trade_deal`, `t_trade_fee_config` 建表SQL + 索引
6. **事务与一致性**
   - 下单事务：冻结资金 + 插入Order 在同一事务
   - 撮合事务：更新Order + 插入Trade + 更新Position + 更新Account 在同一事务
   - 幂等：clientOrderId Redis SETNX 5min + DB UNIQUE
   - 乐观锁：Account.version, Position.version
   - 防超卖：FOR UPDATE 锁定Position行
7. **测试策略**
   - Happy Path: 正常买入100股 → 成交 → 持仓+1
   - 边界: 资金不足 → 拒绝
   - 边界: 涨跌停外委托 → 拒绝
   - 边界: T+1 当日卖出 → 拒绝
   - 并发: 两笔订单同时扣减资金 → 只有一笔成功（乐观锁）

---

## 质量验收标准

- [ ] 下单→冻结→撮合→成交 全链路事务正确
- [ ] clientOrderId 幂等生效（重复提交返回相同订单）
- [ ] 乐观锁冲突不丢单（重试3次）
- [ ] T+1 强制生效
- [ ] 手续费计算精度 ≤ 0.01 元
- [ ] 涨跌停校验覆盖普通/ST/科创板

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 浮点精度 | 金额用 BigDecimal(ROUND_HALF_UP, scale=2) |
| 幂等穿透 | Redis + DB 双重保障 |
| 超卖/超买 | SELECT FOR UPDATE + 乐观锁 |
| 事务过大 | 分离查询与写入，只在写入路径加事务 |
| 撤单竞态 | 撤单前检查 Order.status，用 CAS 更新 |
