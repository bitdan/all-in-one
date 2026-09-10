package com.linger.module.toolhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@ConfigurationProperties(prefix = "toolhub")
public class ToolHubProperties {

    private String corsOrigins = "http://localhost:5173";
    private int captchaTtlSeconds = 300;
    private int chatHistoryLimit = 100;
    private int chatHistoryTtlSeconds = 604800;
    private int gameRoomTtlSeconds = 86400;
    private String totpMasterKey = "";

    public List<String> corsOriginList() {
        if (corsOrigins == null || corsOrigins.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .collect(Collectors.toList());
    }
}
