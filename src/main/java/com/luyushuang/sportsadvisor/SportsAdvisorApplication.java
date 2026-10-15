package com.luyushuang.sportsadvisor;

import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = RedisVectorStoreAutoConfiguration.class)
public class SportsAdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SportsAdvisorApplication.class, args);
    }

}
