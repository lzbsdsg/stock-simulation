# Skill Card: API 契约驱动（OpenAPI）

## 触发句式

> 请帮我设计 {模块名} 的完整 REST API 契约，包含 path、method、请求体、响应体、错误码、鉴权要求。

---

## 输入规范

| 信息 | 说明 |
|---|---|
| 模块名 | auth / market / trade / portfolio / watchlist / admin |
| 版本 | v1 |
| 基础路径 | /api/v1 |
| 鉴权 | Bearer JWT（除公开接口外） |

---

## 输出要求（必须依次输出）

1. **Assumptions** — 分页约定(page/size)、排序约定
2. **接口汇总表** — method + path + 鉴权 + 简述
3. **逐接口详情**（每个接口包含）：
   - HTTP Method + Path
   - 请求头（Authorization 等）
   - Path/Query 参数
   - Request Body (JSON 示例)
   - Response Body (JSON 示例 — 成功 + 失败)
   - 错误码列表
   - 限流规则
4. **错误码汇总** — 模块内所有错误码表

---

## 统一响应格式

```json
{
  "code": "000000",
  "message": "success",
  "data": { ... },
  "traceId": "abc-123-def",
  "timestamp": "2026-02-13T10:30:00Z"
}
```

## 错误响应示例

```json
{
  "code": "TRADE_ORDER_INSUFFICIENT_FUND",
  "message": "可用资金不足",
  "data": null,
  "traceId": "abc-123-def",
  "timestamp": "2026-02-13T10:30:00Z"
}
```

---

## 分页响应格式

```json
{
  "code": "000000",
  "message": "success",
  "data": {
    "records": [ ... ],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

---

## 质量验收标准

- [ ] 所有接口有明确的鉴权说明
- [ ] 请求/响应示例可直接用于 Mock
- [ ] 错误码覆盖所有业务异常场景
- [ ] 分页参数统一
- [ ] 符合 RESTful 最佳实践

---

## 常见陷阱

| 陷阱 | 规避 |
|---|---|
| 接口命名不一致 | 严格RESTful：名词复数 + HTTP动词 |
| 缺少错误码 | 每个接口至少列出3个可能错误码 |
| 响应嵌套太深 | 最多3层嵌套 |
| 未区分公开/私有接口 | 明确标注 `@Public` 或 `@Auth` |
