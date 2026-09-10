package com.linger.module.common.http;

import lombok.Value;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Value
public class HttpStreamResponse {

    int statusCode;
    Map<String, List<String>> headers;
    InputStream body;

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
