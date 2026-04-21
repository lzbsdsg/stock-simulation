package com.lzbsdsg.stocksimulation.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lzbsdsg.stocksimulation.common.cache.CacheInvalidateListener;
import com.lzbsdsg.stocksimulation.market.infrastructure.ingest.MarketPubSubListener;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/** Redis 配置 */
@Configuration
public class RedisConfig {

  @Value("${app.redis.cluster.map-to-loopback:true}")
  private boolean mapClusterNodesToLoopback;

  @Value("${market.redis.pubsub.enabled:true}")
  private boolean marketRedisPubSubEnabled;

  @Bean(destroyMethod = "shutdown")
  @Profile("dev")
  public ClientResources redisClientResources() {
    return DefaultClientResources.builder()
        .socketAddressResolver(
            MappingSocketAddressResolver.create(
                DnsResolvers.UNRESOLVED,
                hostAndPort -> {
                  String host = hostAndPort.getHostText();
                  if (mapClusterNodesToLoopback && host != null && host.startsWith("redis-node-")) {
                    return HostAndPort.of("127.0.0.1", hostAndPort.getPort());
                  }
                  return hostAndPort;
                }))
        .build();
  }

  @Bean
  @Profile("dev")
  public LettuceClientConfigurationBuilderCustomizer lettuceAddressMapperCustomizer(
      ClientResources redisClientResources) {
    return builder -> builder.clientResources(redisClientResources);
  }

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // Key 使用 String 序列化
    StringRedisSerializer stringSerializer = new StringRedisSerializer();
    template.setKeySerializer(stringSerializer);
    template.setHashKeySerializer(stringSerializer);

    // Value 使用 JSON 序列化
    ObjectMapper objectMapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

    GenericJackson2JsonRedisSerializer jsonSerializer =
        new GenericJackson2JsonRedisSerializer(objectMapper);
    template.setValueSerializer(jsonSerializer);
    template.setHashValueSerializer(jsonSerializer);

    template.afterPropertiesSet();
    return template;
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      CacheInvalidateListener cacheInvalidateListener,
      MarketPubSubListener marketPubSubListener) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(cacheInvalidateListener, new PatternTopic("cache:invalidate:*"));
    if (marketRedisPubSubEnabled) {
      container.addMessageListener(marketPubSubListener, new PatternTopic("market:quote:broadcast"));
    }
    return container;
  }
}
