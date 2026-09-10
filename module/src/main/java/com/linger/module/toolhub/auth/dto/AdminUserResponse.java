package com.linger.module.toolhub.auth.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.linger.module.toolhub.auth.UserRecord;
import com.linger.module.toolhub.auth.model.UserStatus;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AdminUserResponse {
    String userId;
    String username;
    String email;
    String avatar;
    UserStatus status;
    List<String> roles;
    List<String> permissions;
    OffsetDateTime createdAt;
    OffsetDateTime updatedAt;

    public static AdminUserResponse from(UserRecord user) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatar(),
                user.getStatus(),
                user.getRoles(),
                user.getPermissions(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
