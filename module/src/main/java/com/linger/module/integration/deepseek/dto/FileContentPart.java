package com.linger.module.integration.deepseek.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public final class FileContentPart implements DeepSeekContentPart {

    private final String type = "file";

    @JsonProperty("file_id")
    private final String fileId;

    public FileContentPart(String fileId) {
        this.fileId = fileId;
    }
}
