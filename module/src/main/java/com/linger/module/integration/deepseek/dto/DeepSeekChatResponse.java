package com.linger.module.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeepSeekChatResponse {

    private String id;
    private String model;
    private List<Choice> choices = Collections.emptyList();
    private Usage usage;

    public String firstContent() {
        if (choices == null || choices.isEmpty() || choices.get(0).getMessage() == null) {
            return null;
        }
        return choices.get(0).getMessage().getContent();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private int index;
        private ResponseMessage message;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResponseMessage {
        private String role;
        private String content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private long promptTokens;

        @JsonProperty("completion_tokens")
        private long completionTokens;

        @JsonProperty("total_tokens")
        private long totalTokens;

        @JsonProperty("prompt_cache_hit_tokens")
        private long promptCacheHitTokens;

        @JsonProperty("prompt_cache_miss_tokens")
        private long promptCacheMissTokens;
    }
}
