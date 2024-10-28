package com.streamsegmenter.config;

import com.streamsegmenter.model.ScheduledStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, ScheduledStream> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, ScheduledStream> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use Jackson serializer for ScheduledStream objects
        Jackson2JsonRedisSerializer<ScheduledStream> serializer =
                new Jackson2JsonRedisSerializer<>(ScheduledStream.class);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use Jackson serializer for values
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();

        return template;
    }
}
