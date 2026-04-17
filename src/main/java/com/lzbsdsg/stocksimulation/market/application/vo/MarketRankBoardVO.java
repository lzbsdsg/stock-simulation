package com.lzbsdsg.stocksimulation.market.application.vo;

import java.util.List;

/** 行情涨跌幅榜 VO */
public record MarketRankBoardVO(List<QuoteVO> gainers, List<QuoteVO> losers) {}
