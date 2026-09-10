package com.linger.module.common.http;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "http.client")
public class HttpClientProperties {

    private long connectTimeoutMillis = 10_000;
    private long readTimeoutMillis = 600_000;
    private long writeTimeoutMillis = 60_000;
    private long callTimeoutMillis = 0;
    private int maxResponseBodyBytes = 16 * 1024 * 1024;
}
