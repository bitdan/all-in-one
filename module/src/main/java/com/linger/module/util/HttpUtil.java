package com.linger.module.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.linger.module.common.http.HttpClientProperties;
import com.linger.module.common.http.HttpRequestData;
import com.linger.module.common.http.HttpRequestException;
import com.linger.module.common.http.HttpResponseData;
import com.linger.module.common.http.HttpResponseTooLargeException;
import com.linger.module.common.http.HttpStreamHandler;
import com.linger.module.common.http.HttpStreamResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通用 HTTP 出站请求工具。
 *
 * <p>该类只负责 HTTP 传输、资源释放和 JSON 编解码，不包含任何第三方服务的协议或鉴权逻辑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpUtil {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final byte[] EMPTY_BODY = new byte[0];

    private final OkHttpClient client;
    private final HttpClientProperties properties;

    public HttpResponseData get(String url, Map<String, String> headers) {
        return execute(HttpRequestData.builder()
                .method("GET")
                .url(url)
                .headers(safeHeaders(headers))
                .build());
    }

    public HttpResponseData postJson(String url, Map<String, String> headers, Object body) {
        return postJson(url, headers, body, null, null);
    }

    public HttpResponseData postJson(String url,
                                     Map<String, String> headers,
                                     Object body,
                                     Long callTimeoutMillis,
                                     Integer maxResponseBodyBytes) {
        return execute(HttpRequestData.builder()
                .method("POST")
                .url(url)
                .headers(safeHeaders(headers))
                .body(toJsonBytes(body))
                .mediaType(JSON.toString())
                .callTimeoutMillis(callTimeoutMillis)
                .maxResponseBodyBytes(maxResponseBodyBytes)
                .build());
    }

    public HttpResponseData execute(HttpRequestData requestData) {
        validate(requestData);
        Request request = buildRequest(requestData);
        Call call = buildCall(request, requestData.getCallTimeoutMillis());
        long startedAt = System.nanoTime();

        try (Response response = call.execute()) {
            byte[] body = readBody(response.body(), responseBodyLimit(requestData));
            HttpResponseData result = new HttpResponseData(
                    response.code(),
                    response.headers().toMultimap(),
                    body
            );
            log.debug("HTTP {} {} completed with status {} in {} ms",
                    request.method(), safeUrl(request), response.code(), elapsedMillis(startedAt));
            return result;
        } catch (HttpResponseTooLargeException e) {
            call.cancel();
            throw e;
        } catch (IOException e) {
            throw new HttpRequestException(
                    "HTTP " + request.method() + " " + safeUrl(request) + " failed", e);
        }
    }

    /**
     * 在响应仍保持打开时调用处理器，处理器返回后自动关闭响应和响应体。
     */
    public void executeStream(HttpRequestData requestData, HttpStreamHandler handler) {
        validate(requestData);
        if (handler == null) {
            throw new IllegalArgumentException("stream handler must not be null");
        }
        Request request = buildRequest(requestData);
        Call call = buildCall(request, requestData.getCallTimeoutMillis());
        long startedAt = System.nanoTime();

        try (Response response = call.execute()) {
            ResponseBody responseBody = response.body();
            InputStream stream = responseBody == null
                    ? new ByteArrayInputStream(EMPTY_BODY)
                    : responseBody.byteStream();
            handler.handle(new HttpStreamResponse(
                    response.code(),
                    response.headers().toMultimap(),
                    stream
            ));
            log.debug("Streaming HTTP {} {} completed with status {} in {} ms",
                    request.method(), safeUrl(request), response.code(), elapsedMillis(startedAt));
        } catch (IOException e) {
            call.cancel();
            throw new HttpRequestException(
                    "Streaming HTTP " + request.method() + " " + safeUrl(request) + " failed", e);
        }
    }

    public <T> T readJson(HttpResponseData response, Class<T> responseType) {
        try {
            return JsonUtils.objectMapper.readValue(response.getBody(), responseType);
        } catch (IOException e) {
            throw new HttpRequestException("Failed to deserialize HTTP response", e);
        }
    }

    public <T> T readJson(HttpResponseData response, TypeReference<T> responseType) {
        try {
            return JsonUtils.objectMapper.readValue(response.getBody(), responseType);
        } catch (IOException e) {
            throw new HttpRequestException("Failed to deserialize HTTP response", e);
        }
    }

    private byte[] toJsonBytes(Object body) {
        try {
            return JsonUtils.objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            throw new HttpRequestException("Failed to serialize HTTP request body", e);
        }
    }

    private Request buildRequest(HttpRequestData requestData) {
        Request.Builder builder = new Request.Builder().url(requestData.getUrl());
        safeHeaders(requestData.getHeaders()).forEach(builder::header);

        byte[] body = requestData.getBody();
        if (body == null && requiresRequestBody(requestData.getMethod())) {
            body = EMPTY_BODY;
        }
        MediaType mediaType = parseMediaType(requestData.getMediaType());
        RequestBody requestBody = body == null ? null : RequestBody.create(body, mediaType);
        return builder.method(requestData.getMethod().toUpperCase(Locale.ROOT), requestBody).build();
    }

    private Call buildCall(Request request, Long timeoutMillis) {
        Call call = client.newCall(request);
        if (timeoutMillis != null) {
            if (timeoutMillis < 0) {
                throw new IllegalArgumentException("call timeout must be greater than or equal to zero");
            }
            call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        return call;
    }

    private byte[] readBody(ResponseBody responseBody, int limit) throws IOException {
        if (responseBody == null) {
            return EMPTY_BODY;
        }
        long contentLength = responseBody.contentLength();
        if (contentLength > limit) {
            throw new HttpResponseTooLargeException(limit);
        }

        try (InputStream input = responseBody.byteStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream(
                     contentLength > 0 ? (int) Math.min(contentLength, limit) : 1024)) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int length;
            while ((length = input.read(buffer)) != -1) {
                total += length;
                if (total > limit) {
                    throw new HttpResponseTooLargeException(limit);
                }
                output.write(buffer, 0, length);
            }
            return output.toByteArray();
        }
    }

    private int responseBodyLimit(HttpRequestData requestData) {
        Integer requestLimit = requestData.getMaxResponseBodyBytes();
        return requestLimit == null ? properties.getMaxResponseBodyBytes() : requestLimit;
    }

    private void validate(HttpRequestData requestData) {
        if (requestData == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (isBlank(requestData.getMethod())) {
            throw new IllegalArgumentException("HTTP method must not be blank");
        }
        if (isBlank(requestData.getUrl())) {
            throw new IllegalArgumentException("HTTP URL must not be blank");
        }
        if (responseBodyLimit(requestData) <= 0) {
            throw new IllegalArgumentException("max response body bytes must be greater than zero");
        }
    }

    private static boolean requiresRequestBody(String method) {
        String normalized = method.toUpperCase(Locale.ROOT);
        return "POST".equals(normalized)
                || "PUT".equals(normalized)
                || "PATCH".equals(normalized)
                || "PROPPATCH".equals(normalized)
                || "REPORT".equals(normalized);
    }

    private static Map<String, String> safeHeaders(Map<String, String> headers) {
        return headers == null ? Collections.emptyMap() : headers;
    }

    private static MediaType parseMediaType(String value) {
        if (value == null) {
            return null;
        }
        MediaType mediaType = MediaType.parse(value);
        if (mediaType == null) {
            throw new IllegalArgumentException("invalid HTTP media type: " + value);
        }
        return mediaType;
    }

    private static String safeUrl(Request request) {
        return request.url().scheme() + "://" + request.url().host() + request.url().encodedPath();
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
