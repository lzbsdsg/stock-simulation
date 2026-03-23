# Skill Card: 持仓与资产（Portfolio）

## 触发句式

> 请帮我生成持仓与资产模块（portfolio），包含资产总览、持仓明细、浮动盈亏、收益曲线、资金流水。

---

## 输入规范

| 信息 | 默认值 |
|---|---|
| 初始资金范围 | 10,000 ~ 1,000,000 |
| 资产快照频率 | 每交易日收盘后 |
| 成本价算法 | 加权平均成本法 |
| 收益率计算 | (当前总资产 - 初始资金) / 初始资金 × 100% |
| 最大回撤 | 历史峰值到谷值的最大跌幅 |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 快照触发方式（定时任务）、分页规则
2. **目录树** — `com.lzbsdsg.stocksimulation.portfolio` 与 `com.lzbsdsg.stocksimulation.user`
3. **核心类职责**
   - `PortfolioController` — 资产总览 / 持仓列表 / 流水 / 收益曲线
   - `PortfolioApplicationService` — 聚合Account + Position + 行情 → 实时计算
   - `Account` — 领域实体（总资产/可用/冻结/version）
   - `Position` — 领域实体（持仓量/冻结量/成本价/version）
   - `AssetSnapshot` — 每日快照值对象
   - `FundFlow` — 资金流水值对象
   - `AccountRepository` / `PositionRepository` — 持久化抽象
   - `AssetSnapshotScheduler` — 每日收盘后快照定时任务
4. **API 契约** — GET /portfolio/overview, GET /portfolio/positions, GET /portfolio/flows, GET /portfolio/equity-curve, POST /user/account/init
5. **Flyway SQL** — `t_user_account`, `t_portfolio_position`, `t_portfolio_fund_flow`, `t_portfolio_asset_snapshot`
6. **事务与一致性**
   - 资产总览为实时计算（Account + 持仓市值），不存DB
   - 持仓更新与资金变动必须同一事务
   - 快照为异步定时，失败可重跑（幂等：同一user+日期唯一）
7. **测试策略**
   - Happy: 买入后持仓出现 → 卖出后持仓消失
   - 边界: 初始资金超限 → 拒绝
   - 边界: 快照重复执行 → 幂等（不重复插入）
   - 计算: 成本价、浮动盈亏精度校验

---

## 质量验收标准

- [ ] 可用资金 + 冻结资金 + 持仓市值 = 总资产（恒等式）
- [ ] 成本价计算精度正确（加权平均）
- [ ] 快照幂等（同日不重复）
- [ ] 流水可追溯每一笔资金变动

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 资产不平 | 每笔交易后校验恒等式 |
| 成本价累积误差 | BigDecimal scale=4 中间计算 |
| 快照缺失 | 定时任务失败告警 + 手动补跑接口 |
| 持仓为0未清理 | 卖出后quantity=0标记，不物理删除 |
