package com.linger.module.toolhub.twofactor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.linger.module.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TotpAlgorithm {
    SHA1("SHA1"),
    SHA256("SHA256"),
    SHA512("SHA512");

    @JsonValue
    private final String value;

    @JsonCreator
    public static TotpAlgorithm fromValue(String value) {
        if (value != null) {
            String normalized = value.trim().toUpperCase().replace("-", "");
            for (TotpAlgorithm algorithm : values()) {
                if (algorithm.value.equals(normalized)) {
                    return algorithm;
                }
            }
        }
        throw BusinessException.badRequest("仅支持 SHA1 / SHA256 / SHA512");
    }
}
