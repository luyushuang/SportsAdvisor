package com.luyushuang.sportsadvisor.config;

import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClient;

@Configuration
public class RedisChatMemoryConfig {

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(RedisClient redisClient) {
        return RedisChatMemoryRepository.builder()
                .jedisClient(redisClient)                    // Redis 客户端
                .indexName("chat-memory-index")             // 索引名称
                .keyPrefix("chat:memory:")                  // Key 前缀
                .initializeSchema(true)                     // 自动初始化 Schema
                .maxConversationIds(1000)                   // 最大返回会话数
                .maxMessagesPerConversation(100)            // 每个会话最大消息数
                .build();
    }
}
