package com.lzbsdsg.stocksimulation.trade.application.command;

import jakarta.validation.constraints.NotNull;

/** 撤单命令 */
public record CancelOrderCommand(

    /** 订单ID */
    @NotNull(message = "订单ID不能为空") Long orderId) {}
