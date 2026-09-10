package com.linger.module.redisson;

import com.linger.module.util.JsonUtils;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @version 1.0
 * @description RedissonConfig
 * @date 2025/7/30 16:51:42
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonAutoConfigurationCustomizer redissonCustomizer() {

        return config -> {
            JsonJacksonCodec codec = new JsonJacksonCodec(JsonUtils.objectMapper);
            config.setCodec(codec);
        };
    }
}
