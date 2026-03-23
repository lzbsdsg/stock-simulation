package com.lzbsdsg.stocksimulation.admin.application;

import com.lzbsdsg.stocksimulation.common.result.PageResult;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 管理后台应用服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminApplicationService {

  // TODO: 注入各模块仓储

  public PageResult<Map<String, Object>> listUsers(int page, int size) {
    // TODO: 查询用户列表（含账户余额、持仓数等）
    throw new UnsupportedOperationException("listUsers not implemented");
  }

  public void toggleUserStatus(Long userId, String status) {
    // TODO: 禁用/启用用户
    throw new UnsupportedOperationException("toggleUserStatus not implemented");
  }

  public Map<String, Object> getDashboardStats() {
    // TODO: 系统总用户数、今日活跃、今日交易笔数、总交易额等统计
    throw new UnsupportedOperationException("getDashboardStats not implemented");
  }

  public PageResult<Map<String, Object>> getLeaderboard(int page, int size) {
    // TODO: 按收益率排名
    throw new UnsupportedOperationException("getLeaderboard not implemented");
  }
}
