package com.linger.module.util;

import com.linger.module.common.http.HttpClientProperties;
import com.linger.module.common.http.HttpRequestData;
import com.linger.module.common.http.HttpResponseData;
import com.linger.module.common.http.HttpResponseTooLargeException;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpUtilTest {

    private MockWebServer server;
    private HttpUtil httpUtil;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        HttpClientProperties properties = new HttpClientProperties();
        properties.setMaxResponseBodyBytes(1024);
        httpUtil = new HttpUtil(new OkHttpClient(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldSerializeJsonAndReturnDetachedResponse() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(201)
                .addHeader("X-Request-Id", "request-1")
                .setBody("{\"result\":\"ok\"}"));

        HttpResponseData response = httpUtil.postJson(
                server.url("/items").toString(),
                Collections.singletonMap("Authorization", "Bearer secret"),
                Collections.singletonMap("name", "demo")
        );

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret");
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"name\":\"demo\"}");
        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.bodyAsString()).isEqualTo("{\"result\":\"ok\"}");
        assertThat(response.headerValues("x-request-id")).containsExactly("request-1");
    }

    @Test
    void shouldRejectOversizedBufferedResponse() {
        server.enqueue(new MockResponse().setBody(new String(new char[64]).replace('\0', 'a')));

        HttpRequestData request = HttpRequestData.builder()
                .method("GET")
                .url(server.url("/large").toString())
                .maxResponseBodyBytes(16)
                .build();

        assertThatThrownBy(() -> httpUtil.execute(request))
                .isInstanceOf(HttpResponseTooLargeException.class)
                .hasMessageContaining("16 bytes");
    }

    @Test
    void shouldKeepStreamOpenDuringHandlerAndCloseAfterwards() {
        server.enqueue(new MockResponse().setBody("data: hello\n\ndata: [DONE]\n\n"));
        AtomicReference<String> received = new AtomicReference<>();

        httpUtil.executeStream(HttpRequestData.builder()
                .method("GET")
                .url(server.url("/events").toString())
                .build(), response -> {
            byte[] body = new byte[128];
            int length = response.getBody().read(body);
            received.set(new String(body, 0, length, java.nio.charset.StandardCharsets.UTF_8));
        });

        assertThat(received.get()).contains("data: hello").contains("data: [DONE]");
    }
}
