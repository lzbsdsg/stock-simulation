package com.lzbsdsg.stocksimulation.trade.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** 下单命令 */
public record PlaceOrderCommand(

    /** 客户端幂等键（UUID） */
    @NotBlank(message = "clientOrderId不能为空") String clientOrderId,

    /** 股票代码 */
    @NotBlank(message = "股票代码不能为空") String stockCode,

    /** 买卖方向: BUY / SELL */
    @NotBlank(message = "交易方向不能为空") String side,

    /** 订单类型: LIMIT=限价 / MARKET=市价 */
    @NotBlank(message = "订单类型不能为空") String orderType,

    /** 委托价格（限价单必填） */
    @DecimalMin(value = "0.01", message = "委托价格必须大于0") BigDecimal price,

    /** 委托数量（必须为100的整数倍） */
    @NotNull(message = "委托数量不能为空") @Min(value = 100, message = "委托数量最少100股") Integer quantity) {}
