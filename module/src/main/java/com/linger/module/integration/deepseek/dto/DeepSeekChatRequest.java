package com.linger.module.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepSeekChatRequest {

    private String model;
    private List<DeepSeekMessage> messages;
    private Boolean stream;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Double temperature;

    @JsonProperty("user_id")
    private String userId;
}
