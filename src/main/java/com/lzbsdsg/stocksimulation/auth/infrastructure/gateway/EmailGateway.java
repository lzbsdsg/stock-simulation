package com.lzbsdsg.stocksimulation.auth.infrastructure.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 邮件发送网关
 *
 * <p>通过 SMTP 发送验证码邮件。
 */
@Component
public class EmailGateway {

  private static final Logger log = LoggerFactory.getLogger(EmailGateway.class);

  // TODO: 注入 JavaMailSender

  /** 发送验证码邮件 */
  public void sendOtpEmail(String toEmail, String otpCode) {
    // TODO: 实现邮件发送
    // Subject: 【股市仿真交易】验证码
    // Body: 您的验证码是: {otpCode}，有效期5分钟。
    log.info("发送验证码到 {}: {}", toEmail, otpCode);
  }
}
