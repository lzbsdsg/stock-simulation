package com.lzbsdsg.stocksimulation.auth.application;

import com.lzbsdsg.stocksimulation.auth.application.command.*;
import com.lzbsdsg.stocksimulation.auth.application.dto.TokenDTO;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务
 *
 * <p>编排 OTP 发送/校验、用户注册、密码登录、Token 签发/刷新/登出。
 */
@Service
public class AuthApplicationService {

  // TODO: 注入 OtpDomainService, PasswordDomainService, UserRepository,
  //       AccountApplicationService, OtpRedisGateway, JwtTokenProvider, EmailGateway

  public void sendOtp(SendOtpCommand command) {
    // TODO: 频率限制检查 → 生成OTP → 存Redis(TTL=5min) → 发送邮件
  }

  public TokenDTO register(RegisterCommand command) {
    // TODO: 验证OTP → 检查邮箱唯一 → 创建User → 创建Account → 签发JWT
    return null;
  }

  public TokenDTO login(LoginCommand command) {
    // TODO: 查询User → 检查锁定 → BCrypt验证 → 清零/增加失败计数 → 签发JWT
    return null;
  }

  public TokenDTO loginByOtp(LoginByOtpCommand command) {
    // TODO: 验证OTP → 查询User → 签发JWT
    return null;
  }

  public TokenDTO refreshToken(RefreshTokenCommand command) {
    // TODO: 校验RefreshToken → Rotation(旧token立即失效) → 签发新JWT对
    return null;
  }

  public void logout(String authorization) {
    // TODO: 解析token → 加入Redis黑名单(TTL=剩余过期时间)
  }

  public void resetPassword(ResetPasswordCommand command) {
    // TODO: 验证OTP → 校验密码强度 → 更新密码hash
  }
}
