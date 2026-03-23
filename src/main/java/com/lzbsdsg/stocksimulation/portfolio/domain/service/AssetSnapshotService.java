package com.lzbsdsg.stocksimulation.portfolio.domain.service;

/**
 * 资产快照服务（领域服务）
 *
 * <p>负责每日收盘后拍快照，计算当日收益与累计收益率。
 */
public class AssetSnapshotService {

  /**
   * 创建每日资产快照
   *
   * <p>由调度器在收盘后调用： 1. 获取用户账户余额 2. 获取所有持仓 + 最新收盘价 → 计算市值 3. 总资产 = 余额 + 市值 4. 与前一日快照比较计算日收益 5. 写入
   * AssetSnapshot
   */
  // TODO: 入参与返回类型根据实际实现补充
}
