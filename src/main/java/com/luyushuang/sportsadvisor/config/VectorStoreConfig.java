package com.luyushuang.sportsadvisor.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClient;

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(RedisClient redisClient, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(redisClient, embeddingModel)
                .indexName("running-knowledge-index")
                .prefix("running:knowledge:")
                .metadataFields(
                        MetadataField.tag("version"),
                        MetadataField.tag("category"),
                        MetadataField.tag("source")
                )
                .initializeSchema(true)
                .build();
    }

    @Bean
    public RedisClient redisClient() {
        return RedisClient.builder()
                .hostAndPort("localhost", 6379)
                .build();
    }
}
