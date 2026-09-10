package com.linger.module.integration.deepseek.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class DeepSeekConfiguration {
}
