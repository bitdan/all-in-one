package com.linger.module.integration.deepseek.client;

import com.linger.module.common.http.HttpRequestException;
import com.linger.module.common.http.HttpResponseData;
import com.linger.module.integration.deepseek.config.DeepSeekProperties;
import com.linger.module.integration.deepseek.dto.DeepSeekChatRequest;
import com.linger.module.integration.deepseek.dto.DeepSeekChatResponse;
import com.linger.module.integration.deepseek.dto.DeepSeekErrorResponse;
import com.linger.module.integration.deepseek.exception.DeepSeekApiException;
import com.linger.module.util.HttpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private final HttpUtil httpUtil;
    private final DeepSeekProperties properties;

    public DeepSeekChatResponse createChatCompletion(DeepSeekChatRequest request) {
        validateConfiguration();
        if (request == null) {
            throw new IllegalArgumentException("DeepSeek request must not be null");
        }

        DeepSeekChatRequest actualRequest = request;
        if (isBlank(request.getModel())) {
            actualRequest = request.toBuilder().model(properties.getModel()).build();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + properties.getApiKey());
        headers.put("Accept", "application/json");

        HttpResponseData response = httpUtil.postJson(
                properties.chatCompletionsUrl(),
                headers,
                actualRequest,
                properties.getCallTimeoutMillis(),
                properties.getMaxResponseBodyBytes()
        );
        if (!response.isSuccessful()) {
            throw toApiException(response);
        }
        return httpUtil.readJson(response, DeepSeekChatResponse.class);
    }

    private DeepSeekApiException toApiException(HttpResponseData response) {
        String errorCode = null;
        String message = "DeepSeek API request failed with HTTP " + response.getStatusCode();
        try {
            DeepSeekErrorResponse errorResponse = httpUtil.readJson(response, DeepSeekErrorResponse.class);
            if (errorResponse != null && errorResponse.getError() != null) {
                errorCode = errorResponse.getError().getCode();
                if (!isBlank(errorResponse.getError().getMessage())) {
                    message = errorResponse.getError().getMessage();
                }
            }
        } catch (HttpRequestException ignored) {
            // 非 JSON 错误体仍然保留 HTTP 状态码，不把可能包含敏感信息的原始响应带到异常中。
        }
        return new DeepSeekApiException(response.getStatusCode(), errorCode, message);
    }

    private void validateConfiguration() {
        if (isBlank(properties.getApiKey())) {
            throw new IllegalStateException("DeepSeek API key is not configured");
        }
        if (isBlank(properties.getBaseUrl())) {
            throw new IllegalStateException("DeepSeek base URL is not configured");
        }
        if (isBlank(properties.getModel())) {
            throw new IllegalStateException("DeepSeek model is not configured");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
