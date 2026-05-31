package org.example.realtime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.example.realtime.listener.RedisPubSubListener;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // Cú pháp pattern để lắng nghe TẤT CẢ các kênh (channel) dạng qms:events:branch:*
        container.addMessageListener(listenerAdapter, new PatternTopic("qms:events:branch:*"));
        
        // Cú pháp pattern để lắng nghe TẤT CẢ các kênh counter session dạng qms:events:counter:session:*
        container.addMessageListener(listenerAdapter, new PatternTopic("qms:events:counter:session:*"));

        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisPubSubListener redisPubSubListener) {
        return new MessageListenerAdapter(redisPubSubListener, "handleRedisMessage");
    }
}

