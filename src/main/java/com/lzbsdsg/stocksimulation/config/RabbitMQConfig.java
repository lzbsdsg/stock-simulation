package com.lzbsdsg.stocksimulation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 配置 */
@Configuration
public class RabbitMQConfig {

  public static final String TRADE_EXCHANGE = "trade.exchange";
  public static final String MATCH_QUEUE = "trade.match.queue";
  public static final String MATCH_ROUTING_KEY = "trade.match";
  public static final String NOTIFICATION_QUEUE = "trade.notification.queue";
  public static final String NOTIFICATION_ROUTING_KEY = "trade.notification";
  public static final String EMAIL_EXCHANGE = "email.exchange";
  public static final String EMAIL_QUEUE = "email.send.queue";
  public static final String EMAIL_ROUTING_KEY = "email.send";

  @Bean
  public DirectExchange tradeExchange() {
    return new DirectExchange(TRADE_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange emailExchange() {
    return new DirectExchange(EMAIL_EXCHANGE, true, false);
  }

  @Bean
  public Queue matchQueue() {
    return new Queue(MATCH_QUEUE, true);
  }

  @Bean
  public Queue notificationQueue() {
    return new Queue(NOTIFICATION_QUEUE, true);
  }

  @Bean
  public Queue emailQueue() {
    return new Queue(EMAIL_QUEUE, true);
  }

  @Bean
  public Binding matchBinding(Queue matchQueue, DirectExchange tradeExchange) {
    return BindingBuilder.bind(matchQueue).to(tradeExchange).with(MATCH_ROUTING_KEY);
  }

  @Bean
  public Binding notificationBinding(Queue notificationQueue, DirectExchange tradeExchange) {
    return BindingBuilder.bind(notificationQueue).to(tradeExchange).with(NOTIFICATION_ROUTING_KEY);
  }

  @Bean
  public Binding emailBinding(Queue emailQueue, DirectExchange emailExchange) {
    return BindingBuilder.bind(emailQueue).to(emailExchange).with(EMAIL_ROUTING_KEY);
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }
}
