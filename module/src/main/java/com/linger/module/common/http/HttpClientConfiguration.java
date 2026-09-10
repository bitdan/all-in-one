package com.linger.module.common.http;

import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientConfiguration {

    @Bean
    public OkHttpClient outboundOkHttpClient(HttpClientProperties properties) {
        return new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getWriteTimeoutMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(properties.getCallTimeoutMillis(), TimeUnit.MILLISECONDS)
                .build();
    }
}
