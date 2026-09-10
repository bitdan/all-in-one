package com.linger.module.integration.deepseek;

import com.linger.module.common.http.HttpClientConfiguration;
import com.linger.module.integration.deepseek.client.DeepSeekClient;
import com.linger.module.integration.deepseek.config.DeepSeekConfiguration;
import com.linger.module.integration.deepseek.config.DeepSeekProperties;
import com.linger.module.integration.deepseek.dto.DeepSeekChatResponse;
import com.linger.module.integration.deepseek.dto.DeepSeekImageDetail;
import com.linger.module.integration.deepseek.service.DeepSeekVisionService;
import com.linger.module.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DeepSeek 真实 API 冒烟测试。
 *
 * <p>从 application.yml/application-local.yml 绑定 DeepSeek 配置；存在 API Key 时会产生真实网络请求和少量费用。</p>
 */
@Slf4j
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        classes = {
                HttpClientConfiguration.class,
                DeepSeekConfiguration.class,
                HttpUtil.class,
                DeepSeekClient.class,
                DeepSeekVisionService.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "http.client.read-timeout-millis=180000",
                "deepseek.call-timeout-millis=180000"
        }
)
class DeepSeekApiIntegrationTest {

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private DeepSeekVisionService visionService;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @BeforeEach
    void requireApiKey() {
        assumeTrue(!isBlank(deepSeekProperties.getApiKey()),
                "deepseek.api-key is not configured in the local test configuration");
    }

    @AfterAll
    void tearDown() {
        okHttpClient.dispatcher().executorService().shutdown();
        okHttpClient.connectionPool().evictAll();
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void shouldUnderstandGeneratedRedImageThroughRealApi() throws IOException {
        DeepSeekChatResponse response = visionService.analyzeBase64(
                "这是一张程序生成的纯色图片。请只回答图片的主要颜色，不要解释。",
                createRedPng(),
                "image/png",
                DeepSeekImageDetail.LOW,
                "codex-integration-test"
        );

        assertThat(response.getId()).isNotBlank();
        assertThat(response.getChoices()).isNotEmpty();
        assertThat(response.firstContent()).isNotBlank();
        assertThat(response.firstContent().toLowerCase(Locale.ROOT)).containsAnyOf("红", "red");
        assertThat(response.getUsage()).isNotNull();
        assertThat(response.getUsage().getTotalTokens()).isGreaterThan(0);

        log.info("DeepSeek real API test completed, model={}, content={}, totalTokens={}",
                response.getModel(), response.firstContent(), response.getUsage().getTotalTokens());
    }

    private static byte[] createRedPng() throws IOException {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.RED);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) {
                throw new IOException("PNG writer is not available");
            }
            return output.toByteArray();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
