package com.lzbsdsg.stocksimulation.auth.infrastructure.gateway;

import com.lzbsdsg.stocksimulation.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 邮件发送网关
 *
 * <p>通过 SMTP 发送验证码邮件。
 */
@Component
public class EmailGateway {

  private static final Logger log = LoggerFactory.getLogger(EmailGateway.class);

  private final RabbitTemplate rabbitTemplate;

  public EmailGateway(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /** 发送验证码邮件 */
  public void sendOtpEmail(String toEmail, String otpCode) {
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.EMAIL_EXCHANGE,
        RabbitMQConfig.EMAIL_ROUTING_KEY,
        new EmailMessage(toEmail, "【股市仿真交易】验证码", "您的验证码是: " + otpCode + "，有效期5分钟。"));
    log.info("发送验证码到 {}: {}", toEmail, otpCode);
  }

  public record EmailMessage(String to, String subject, String content) {}
}
