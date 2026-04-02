package com.lzbsdsg.stocksimulation.market.application.vo;

/** 行情中心股票分页项 */
public record StockListItemVO(
    String stockCode, String stockName, String market, String boardType, String industry) {}
