package com.lzbsdsg.stocksimulation.auth.infrastructure.gateway;

import org.springframework.stereotype.Component;

/**
 * JWT Token 提供者
 *
 * <p>负责 JWT 签发、解析、黑名单管理。
 */
@Component
public class JwtTokenProvider {

  // TODO: 注入 secret key 配置、RedisTemplate

  /** 签发 Access Token（30min） */
  public String generateAccessToken(Long userId, String email, String role) {
    // TODO: 使用 jjwt/nimbus-jose-jwt 创建 JWT
    return null;
  }

  /** 签发 Refresh Token（7d） */
  public String generateRefreshToken(Long userId) {
    // TODO: 创建 Refresh Token 并存入 Redis
    return null;
  }

  /** 解析 Token，获取用户ID */
  public Long getUserIdFromToken(String token) {
    // TODO: 解析 JWT Claims
    return null;
  }

  /** 验证 Token 有效性（签名 + 过期 + 黑名单） */
  public boolean validateToken(String token) {
    // TODO: 校验签名 + 过期时间 + Redis黑名单检查
    return false;
  }

  /** 将 Token 加入黑名单（Redis，TTL=剩余过期时间） */
  public void addToBlacklist(String token) {
    // TODO: SET jwt:blacklist:{jti}, TTL=remaining
  }

  /** 验证 Refresh Token 并执行 Rotation */
  public boolean validateRefreshToken(String refreshToken, Long userId) {
    // TODO: 校验 Redis 中的 Refresh Token 是否匹配
    return false;
  }

  /** 使旧 Refresh Token 失效 */
  public void revokeRefreshToken(Long userId) {
    // TODO: DEL jwt:refresh:{userId}
  }
}
