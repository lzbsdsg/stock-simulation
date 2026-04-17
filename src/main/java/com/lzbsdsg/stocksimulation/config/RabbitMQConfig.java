package com.lzbsdsg.stocksimulation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 配置 */
@Configuration
public class RabbitMQConfig {

  public static final String TRADE_EXCHANGE = "trade.exchange";
  public static final String MATCH_QUEUE = "trade.match.queue";
  public static final String MATCH_ROUTING_KEY = "trade.match";
  public static final String MATCH_DLX = "trade.match.dlx";
  public static final String MATCH_DLQ = "trade.match.dlq";
  public static final String MATCH_DLQ_ROUTING_KEY = "trade.match.dlq";
  public static final String TRADE_FILLED_EXCHANGE = "trade.filled.exchange";
  public static final String NOTIFICATION_QUEUE = "trade.notification.queue";
  public static final String NOTIFICATION_ROUTING_KEY = "trade.notification";
  public static final String EMAIL_EXCHANGE = "email.exchange";
  public static final String EMAIL_QUEUE = "email.send.queue";
  public static final String EMAIL_ROUTING_KEY = "email.send";

  @Value("${app.rabbit.match.prefetch:10}")
  private int matchPrefetch;

  @Value("${app.rabbit.match.concurrent-consumers:8}")
  private int matchConcurrentConsumers;

  @Value("${app.rabbit.match.max-concurrent-consumers:8}")
  private int matchMaxConcurrentConsumers;

  @Bean
  public DirectExchange tradeExchange() {
    return new DirectExchange(TRADE_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange emailExchange() {
    return new DirectExchange(EMAIL_EXCHANGE, true, false);
  }

  @Bean
  public DirectExchange matchDlxExchange() {
    return new DirectExchange(MATCH_DLX, true, false);
  }

  @Bean
  public FanoutExchange tradeFilledExchange() {
    return new FanoutExchange(TRADE_FILLED_EXCHANGE, true, false);
  }

  @Bean
  public Queue matchQueue() {
    return QueueBuilder.durable(MATCH_QUEUE)
        .withArgument("x-dead-letter-exchange", MATCH_DLX)
        .withArgument("x-dead-letter-routing-key", MATCH_DLQ_ROUTING_KEY)
        .build();
  }

  @Bean
  public Queue matchDeadLetterQueue() {
    return QueueBuilder.durable(MATCH_DLQ).build();
  }

  @Bean
  public Queue notificationQueue() {
    return QueueBuilder.durable(NOTIFICATION_QUEUE).build();
  }

  @Bean
  public Queue emailQueue() {
    return new Queue(EMAIL_QUEUE, true);
  }

  @Bean
  public Binding matchBinding(
      @Qualifier("matchQueue") Queue matchQueue,
      @Qualifier("tradeExchange") DirectExchange tradeExchange) {
    return BindingBuilder.bind(matchQueue).to(tradeExchange).with(MATCH_ROUTING_KEY);
  }

  @Bean
  public Binding matchDeadLetterBinding(
      @Qualifier("matchDeadLetterQueue") Queue matchDeadLetterQueue,
      @Qualifier("matchDlxExchange") DirectExchange matchDlxExchange) {
    return BindingBuilder.bind(matchDeadLetterQueue).to(matchDlxExchange).with(MATCH_DLQ_ROUTING_KEY);
  }

  @Bean
  public Binding notificationBinding(
      @Qualifier("notificationQueue") Queue notificationQueue,
      @Qualifier("tradeExchange") DirectExchange tradeExchange) {
    return BindingBuilder.bind(notificationQueue).to(tradeExchange).with(NOTIFICATION_ROUTING_KEY);
  }

  @Bean
  public Binding tradeFilledNotificationBinding(
      @Qualifier("notificationQueue") Queue notificationQueue,
      @Qualifier("tradeFilledExchange") FanoutExchange tradeFilledExchange) {
    return BindingBuilder.bind(notificationQueue).to(tradeFilledExchange);
  }

  @Bean
  public Binding emailBinding(
      @Qualifier("emailQueue") Queue emailQueue,
      @Qualifier("emailExchange") DirectExchange emailExchange) {
    return BindingBuilder.bind(emailQueue).to(emailExchange).with(EMAIL_ROUTING_KEY);
  }

  @Bean
  public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean(name = "matchRabbitListenerContainerFactory")
  public SimpleRabbitListenerContainerFactory matchRabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer,
      ConnectionFactory connectionFactory,
      MessageConverter messageConverter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    int safeConcurrent = Math.max(1, matchConcurrentConsumers);
    int safeMaxConcurrent = Math.max(safeConcurrent, matchMaxConcurrentConsumers);
    factory.setPrefetchCount(Math.max(1, matchPrefetch));
    factory.setConcurrentConsumers(safeConcurrent);
    factory.setMaxConcurrentConsumers(safeMaxConcurrent);
    factory.setDefaultRequeueRejected(false);
    factory.setMessageConverter(messageConverter);
    return factory;
  }
}
