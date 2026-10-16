package com.luyushuang.sportsadvisor.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.luyushuang.sportsadvisor.persistence.mapper")
public class MyBatisConfig {
}
