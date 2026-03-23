package com.lzbsdsg.stocksimulation.common.result;

/**
 * 统一错误码枚举
 *
 * <p>命名规范: MODULE_SCENE_REASON
 */
public enum ErrorCode {

  // ==================== 通用 ====================
  SUCCESS(200, "success"),
  BAD_REQUEST(400, "请求参数错误"),
  UNAUTHORIZED(401, "未认证"),
  FORBIDDEN(403, "无权限"),
  NOT_FOUND(404, "资源不存在"),
  CONFLICT(409, "资源冲突"),
  TOO_MANY_REQUESTS(429, "请求过于频繁"),
  INTERNAL_ERROR(500, "服务器内部错误"),

  // ==================== AUTH 认证模块 ====================
  AUTH_OTP_SEND_TOO_FREQUENT(10001, "验证码发送过于频繁，请60秒后重试"),
  AUTH_OTP_INVALID(10002, "验证码无效或已过期"),
  AUTH_OTP_IP_LIMIT(10003, "该IP发送验证码次数已达上限"),
  AUTH_EMAIL_ALREADY_REGISTERED(10004, "该邮箱已注册"),
  AUTH_PASSWORD_TOO_WEAK(10005, "密码强度不足：至少8位，包含大写+小写+数字"),
  AUTH_LOGIN_FAILED(10006, "邮箱或密码错误"),
  AUTH_ACCOUNT_LOCKED(10007, "账户已锁定，请30分钟后重试"),
  AUTH_TOKEN_EXPIRED(10008, "Token已过期"),
  AUTH_TOKEN_INVALID(10009, "Token无效"),
  AUTH_REFRESH_TOKEN_INVALID(10010, "Refresh Token无效或已失效"),

  // ==================== USER 用户模块 ====================
  USER_NOT_FOUND(20001, "用户不存在"),
  USER_ACCOUNT_NOT_FOUND(20002, "资金账户不存在"),
  USER_INITIAL_BALANCE_INVALID(20003, "初始资金范围: 10000~1000000"),

  // ==================== MARKET 行情模块 ====================
  MARKET_STOCK_NOT_FOUND(30001, "股票不存在"),
  MARKET_DATA_UNAVAILABLE(30002, "行情数据暂不可用"),
  MARKET_PROVIDER_ALL_FAILED(30003, "所有行情数据源均不可用"),

  // ==================== TRADE 交易模块 ====================
  TRADE_ORDER_MARKET_CLOSED(40001, "当前非交易时间"),
  TRADE_ORDER_PRICE_LIMIT(40002, "委托价格超出涨跌停限制"),
  TRADE_ORDER_QUANTITY_INVALID(40003, "委托数量须为100的整数倍"),
  TRADE_ORDER_INSUFFICIENT_FUND(40004, "可用资金不足"),
  TRADE_ORDER_INSUFFICIENT_POSITION(40005, "可用持仓不足"),
  TRADE_ORDER_T_PLUS_1(40006, "T+1限制：当日买入股票不可卖出"),
  TRADE_ORDER_DUPLICATE(40007, "重复提交订单"),
  TRADE_ORDER_NOT_FOUND(40008, "委托订单不存在"),
  TRADE_ORDER_CANNOT_CANCEL(40009, "订单状态不允许撤单"),
  TRADE_ORDER_NOT_OWN(40010, "无权操作他人订单"),
  TRADE_OPTIMISTIC_LOCK_CONFLICT(40011, "操作冲突，请重试"),

  // ==================== PORTFOLIO 持仓模块 ====================
  PORTFOLIO_POSITION_NOT_FOUND(50001, "持仓不存在"),
  PORTFOLIO_SNAPSHOT_ALREADY_EXISTS(50002, "当日快照已生成"),

  // ==================== WATCHLIST 自选股模块 ====================
  WATCHLIST_ALREADY_EXISTS(60001, "该股票已在自选列表"),
  WATCHLIST_LIMIT_EXCEEDED(60002, "自选股数量已达上限"),

  // ==================== NOTIFICATION 通知模块 ====================
  NOTIFICATION_NOT_FOUND(70001, "消息不存在");

  private final int code;
  private final String message;

  ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
