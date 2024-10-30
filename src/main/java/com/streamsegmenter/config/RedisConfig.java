package com.streamsegmenter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

        // Create and configure ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule()); // Register JavaTimeModule for LocalDateTime

        // Create Jackson2JsonRedisSerializer with the custom ObjectMapper
        Jackson2JsonRedisSerializer<ScheduledStream> serializer =
                new Jackson2JsonRedisSerializer<>(ScheduledStream.class);
        serializer.setObjectMapper(objectMapper); // Use the custom ObjectMapper

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
