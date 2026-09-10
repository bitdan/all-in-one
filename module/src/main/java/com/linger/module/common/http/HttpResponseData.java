package com.linger.module.common.http;

import lombok.Value;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value
public class HttpResponseData {

    int statusCode;
    Map<String, List<String>> headers;
    byte[] body;

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public List<String> headerValues(String name) {
        if (headers == null || name == null) {
            return Collections.emptyList();
        }
        return headers.entrySet().stream()
                .filter(entry -> name.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(Collections.emptyList());
    }
}
