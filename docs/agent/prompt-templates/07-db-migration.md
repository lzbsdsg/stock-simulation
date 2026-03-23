# Skill Card: 数据库建模与迁移（DB Migration）

## 触发句式

> 请帮我生成 {模块名} 的数据库表设计与 Flyway 迁移脚本，包含完整字段、索引、约束。

---

## 输入规范

| 信息 | 说明 |
|---|---|
| 模块名 | 如：user / trade / portfolio / market / watchlist |
| 特殊需求 | 如：需要分区、软删除、审计字段等 |
| 关联关系 | 如：Order → Trade → Position 的外键/逻辑关联 |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 数据库类型(PostgreSQL 16)、字符集、时区
2. **ER 图描述** — 用文本/Mermaid 描述表间关系
3. **表结构明细** — 每张表的完整字段定义（类型、约束、默认值、注释）
4. **索引策略** — 每张表需要的索引（B-tree/联合/唯一/部分索引）
5. **Flyway 脚本** — 完整可执行SQL，版本号 `V{yyyyMMdd}_{seq}__{desc}.sql`
6. **种子数据** — 枚举字典初始数据、测试账号等
7. **注意事项** — 字段精度、时区处理、NULL策略

---

## 命名规范（硬性）

```
表名：       t_{module}_{entity}          → t_trade_order
字段：       snake_case                    → available_quantity
主键：       id BIGSERIAL PRIMARY KEY
审计字段：   created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
             updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
逻辑删除：   deleted_at TIMESTAMPTZ (NULL = 未删除)
乐观锁：     version INTEGER NOT NULL DEFAULT 0
金额字段：   NUMERIC(18,2) — 不用 FLOAT/DOUBLE
```

---

## 质量验收标准

- [ ] 所有表有主键 + 审计字段
- [ ] 金融相关表有 version 字段
- [ ] 金额字段用 NUMERIC(18,2)
- [ ] 索引覆盖高频查询
- [ ] Flyway脚本可直接执行（无语法错误）
- [ ] 枚举值有清晰注释

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 金额精度丢失 | NUMERIC(18,2)，不用FLOAT |
| 时区混乱 | 统一 TIMESTAMPTZ + UTC 存储 |
| 索引过多 | 只索引高频查询路径 |
| 迁移冲突 | 版本号包含日期，团队协调 |
| 大表全扫 | status + created_at 联合索引 |
