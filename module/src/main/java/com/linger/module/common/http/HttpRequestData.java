package com.linger.module.common.http;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class HttpRequestData {

    String method;
    String url;
    @Builder.Default
    Map<String, String> headers = java.util.Collections.emptyMap();
    byte[] body;
    String mediaType;
    Long callTimeoutMillis;
    Integer maxResponseBodyBytes;
}
