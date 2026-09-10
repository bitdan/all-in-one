package com.linger.module.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeepSeekErrorResponse {

    private ErrorDetail error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetail {
        private String message;
        private String type;
        private String param;
        private String code;
    }
}
