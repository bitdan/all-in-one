package com.linger.module.integration.deepseek.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-flash";
    private long callTimeoutMillis = 0;
    private int maxResponseBodyBytes = 16 * 1024 * 1024;

    public String chatCompletionsUrl() {
        return trimTrailingSlash(baseUrl) + "/chat/completions";
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
