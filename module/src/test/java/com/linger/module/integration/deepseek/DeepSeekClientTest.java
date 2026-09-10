package com.linger.module.integration.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linger.module.common.http.HttpClientProperties;
import com.linger.module.integration.deepseek.client.DeepSeekClient;
import com.linger.module.integration.deepseek.config.DeepSeekProperties;
import com.linger.module.integration.deepseek.dto.DeepSeekChatResponse;
import com.linger.module.integration.deepseek.dto.DeepSeekImageDetail;
import com.linger.module.integration.deepseek.exception.DeepSeekApiException;
import com.linger.module.integration.deepseek.service.DeepSeekVisionService;
import com.linger.module.util.HttpUtil;
import com.linger.module.util.JsonUtils;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekClientTest {

    private MockWebServer server;
    private ObjectMapper objectMapper;
    private DeepSeekVisionService visionService;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = JsonUtils.objectMapper;

        HttpClientProperties httpProperties = new HttpClientProperties();
        HttpUtil httpUtil = new HttpUtil(new OkHttpClient(), httpProperties);
        DeepSeekProperties deepSeekProperties = new DeepSeekProperties();
        deepSeekProperties.setBaseUrl(server.url("/").toString());
        deepSeekProperties.setApiKey("test-key");
        deepSeekProperties.setModel("deepseek-flash");
        visionService = new DeepSeekVisionService(new DeepSeekClient(httpUtil, deepSeekProperties));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldSendTypedVisionRequestAndParseResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"chat-1\",\"model\":\"deepseek-flash\",\"choices\":[{\"index\":0,"
                        + "\"message\":{\"role\":\"assistant\",\"content\":\"一只猫\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":3,\"total_tokens\":13}}"));

        DeepSeekChatResponse response = visionService.analyzeImageUrl(
                "图片里有什么？",
                "https://example.com/cat.png",
                DeepSeekImageDetail.LOW,
                "user-1"
        );

        RecordedRequest request = server.takeRequest();
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertThat(request.getPath()).isEqualTo("/chat/completions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(body.path("model").asText()).isEqualTo("deepseek-flash");
        assertThat(body.path("user_id").asText()).isEqualTo("user-1");
        assertThat(body.path("messages").get(0).path("content").get(0).path("type").asText()).isEqualTo("text");
        assertThat(body.path("messages").get(0).path("content").get(1).path("type").asText()).isEqualTo("image_url");
        assertThat(body.path("messages").get(0).path("content").get(1)
                .path("image_url").path("detail").asText()).isEqualTo("low");
        assertThat(response.firstContent()).isEqualTo("一只猫");
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(13);
    }

    @Test
    void shouldMapProviderErrorWithoutLeakingRawBody() {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"rate limited\",\"type\":\"rate_limit_error\",\"code\":\"rate_limit\"}}"));

        assertThatThrownBy(() -> visionService.analyzeImageUrl("describe", "https://example.com/image.jpg"))
                .isInstanceOfSatisfying(DeepSeekApiException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(429);
                    assertThat(exception.getErrorCode()).isEqualTo("rate_limit");
                    assertThat(exception.getMessage()).isEqualTo("rate limited");
                });
    }

    @Test
    void shouldBuildBase64DataUrl() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"choices\":[]}"));

        visionService.analyzeBase64(
                "read",
                new byte[]{1, 2, 3},
                "image/png",
                DeepSeekImageDetail.AUTO,
                null
        );

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        String url = body.path("messages").get(0).path("content").get(1)
                .path("image_url").path("url").asText();
        assertThat(url).isEqualTo("data:image/png;base64,AQID");
    }
}
