package com.lzbsdsg.stocksimulation.auth.infrastructure.mq;

import com.lzbsdsg.stocksimulation.auth.infrastructure.gateway.EmailGateway.EmailMessage;
import com.lzbsdsg.stocksimulation.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 邮件发送消费者
 *
 * <p>消费 email.send 队列消息并通过 SMTP 发送邮件。
 */
@Component
public class EmailSendConsumer {

  private static final Logger log = LoggerFactory.getLogger(EmailSendConsumer.class);

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String fromAddress;

  public EmailSendConsumer(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
  public void onEmailMessage(EmailMessage message) {
    String sender = fromAddress == null ? "" : fromAddress.trim();
    if (sender.isBlank()) {
      log.warn("邮件发送已跳过：未配置 spring.mail.username，to={}", message.to());
      return;
    }

    SimpleMailMessage mail = new SimpleMailMessage();
    mail.setFrom(sender);
    mail.setTo(message.to());
    mail.setSubject(message.subject());
    mail.setText(message.content());
    mailSender.send(mail);

    log.info("邮件发送成功: to={}, subject={}", message.to(), message.subject());
  }
}
