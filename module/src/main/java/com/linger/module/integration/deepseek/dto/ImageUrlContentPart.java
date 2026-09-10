package com.linger.module.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public final class ImageUrlContentPart implements DeepSeekContentPart {

    private final String type = "image_url";

    @JsonProperty("image_url")
    private final ImageUrl imageUrl;

    public ImageUrlContentPart(String url, DeepSeekImageDetail detail) {
        this.imageUrl = new ImageUrl(url, detail);
    }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class ImageUrl {

        private final String url;
        private final DeepSeekImageDetail detail;

        public ImageUrl(String url, DeepSeekImageDetail detail) {
            this.url = url;
            this.detail = detail;
        }
    }
}
