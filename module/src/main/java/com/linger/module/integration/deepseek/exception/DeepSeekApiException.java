package com.linger.module.integration.deepseek.exception;

import lombok.Getter;

@Getter
public class DeepSeekApiException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public DeepSeekApiException(int statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }
}
