package com.linger.module.toolhub.twofactor.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TwoFactorAccountRequest {

    private String label;
    private String issuer;
    private String accountName;
    private String secret;
    private Integer digits;
    private Integer period;
    private String algorithm;
    private String otpauthUri;

    public Map<String, Object> toMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        putIfNotNull(values, "label", label);
        putIfNotNull(values, "issuer", issuer);
        putIfNotNull(values, "accountName", accountName);
        putIfNotNull(values, "secret", secret);
        putIfNotNull(values, "digits", digits);
        putIfNotNull(values, "period", period);
        putIfNotNull(values, "algorithm", algorithm);
        putIfNotNull(values, "otpauthUri", otpauthUri);
        return values;
    }

    private void putIfNotNull(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }
}
