package com.linger.module.integration.deepseek.service;

import com.linger.module.integration.deepseek.client.DeepSeekClient;
import com.linger.module.integration.deepseek.dto.DeepSeekChatRequest;
import com.linger.module.integration.deepseek.dto.DeepSeekChatResponse;
import com.linger.module.integration.deepseek.dto.DeepSeekContentPart;
import com.linger.module.integration.deepseek.dto.DeepSeekImageDetail;
import com.linger.module.integration.deepseek.dto.DeepSeekMessage;
import com.linger.module.integration.deepseek.dto.FileContentPart;
import com.linger.module.integration.deepseek.dto.ImageUrlContentPart;
import com.linger.module.integration.deepseek.dto.TextContentPart;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DeepSeekVisionService {

    private static final int MAX_INLINE_IMAGE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_IMAGE_URL_LENGTH = 8192;
    private static final Pattern USER_ID_PATTERN = Pattern.compile("[a-zA-Z0-9\\-_]{1,512}");
    private static final Set<String> SUPPORTED_MEDIA_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    )));

    private final DeepSeekClient deepSeekClient;

    public DeepSeekChatResponse analyzeImageUrl(String prompt, String imageUrl) {
        return analyzeImageUrl(prompt, imageUrl, DeepSeekImageDetail.AUTO, null);
    }

    public DeepSeekChatResponse analyzeImageUrl(String prompt,
                                                String imageUrl,
                                                DeepSeekImageDetail detail,
                                                String userId) {
        validatePrompt(prompt);
        validateImageUrl(imageUrl);
        return analyze(prompt, new ImageUrlContentPart(imageUrl, defaultDetail(detail)), userId);
    }

    public DeepSeekChatResponse analyzeBase64(String prompt,
                                              byte[] image,
                                              String mediaType,
                                              DeepSeekImageDetail detail,
                                              String userId) {
        validatePrompt(prompt);
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("image must not be empty");
        }
        if (image.length > MAX_INLINE_IMAGE_BYTES) {
            throw new IllegalArgumentException("inline image must not exceed 32 MiB");
        }
        String normalizedMediaType = mediaType == null ? "" : mediaType.toLowerCase();
        if (!SUPPORTED_MEDIA_TYPES.contains(normalizedMediaType)) {
            throw new IllegalArgumentException("unsupported image media type: " + mediaType);
        }
        String dataUrl = "data:" + normalizedMediaType + ";base64," + Base64.getEncoder().encodeToString(image);
        return analyze(prompt, new ImageUrlContentPart(dataUrl, defaultDetail(detail)), userId);
    }

    public DeepSeekChatResponse analyzeFile(String prompt, String fileId, String userId) {
        validatePrompt(prompt);
        if (isBlank(fileId) || !fileId.startsWith("file-api-")) {
            throw new IllegalArgumentException("invalid DeepSeek file id");
        }
        return analyze(prompt, new FileContentPart(fileId), userId);
    }

    private DeepSeekChatResponse analyze(String prompt, DeepSeekContentPart imagePart, String userId) {
        validateUserId(userId);
        DeepSeekChatRequest request = DeepSeekChatRequest.builder()
                .messages(Collections.singletonList(DeepSeekMessage.user(Arrays.asList(
                        new TextContentPart(prompt),
                        imagePart
                ))))
                .stream(false)
                .userId(userId)
                .build();
        return deepSeekClient.createChatCompletion(request);
    }

    private static void validatePrompt(String prompt) {
        if (isBlank(prompt)) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
    }

    private static void validateImageUrl(String imageUrl) {
        if (isBlank(imageUrl) || imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
            throw new IllegalArgumentException("image URL must contain 1 to 8192 characters");
        }
        try {
            URI uri = new URI(imageUrl);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException("image URL must be an absolute HTTP(S) URL");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid image URL", e);
        }
    }

    private static void validateUserId(String userId) {
        if (userId != null && !USER_ID_PATTERN.matcher(userId).matches()) {
            throw new IllegalArgumentException("userId must match [a-zA-Z0-9\\-_]{1,512}");
        }
    }

    private static DeepSeekImageDetail defaultDetail(DeepSeekImageDetail detail) {
        return detail == null ? DeepSeekImageDetail.AUTO : detail;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
