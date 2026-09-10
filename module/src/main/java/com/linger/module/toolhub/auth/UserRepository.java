package com.linger.module.toolhub.auth;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linger.module.toolhub.auth.dto.AdminUserListQuery;
import com.linger.module.toolhub.auth.dto.UserStatusSummary;
import com.linger.module.toolhub.auth.mapper.UserMapper;
import com.linger.module.toolhub.auth.model.UserStatus;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
@AllArgsConstructor
public class UserRepository {

    private final UserMapper userMapper;

    public Optional<UserRecord> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.lambdaQuery(UserRecord.class)
                        .eq(UserRecord::getUsername, username)
                        .ne(UserRecord::getStatus, UserStatus.DELETED)
        ));
    }

    public Optional<UserRecord> findById(String userId) {
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.lambdaQuery(UserRecord.class)
                        .eq(UserRecord::getUserId, userId)
                        .ne(UserRecord::getStatus, UserStatus.DELETED)
        ));
    }

    public boolean existsUsername(String username) {
        return userMapper.selectCount(
                Wrappers.lambdaQuery(UserRecord.class)
                        .eq(UserRecord::getUsername, username)
                        .ne(UserRecord::getStatus, UserStatus.DELETED)
        ) > 0;
    }

    public void insert(UserRecord user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setCreatedBy(user.getUserId());
        user.setUpdatedBy(user.getUserId());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
    }

    public void updateProfile(String userId, String email, String avatar) {
        userMapper.update(null, update(userId)
                .set(UserRecord::getEmail, emptyToNull(email))
                .set(UserRecord::getAvatar, emptyToNull(avatar))
                .set(UserRecord::getUpdatedBy, userId)
                .set(UserRecord::getUpdatedAt, now()));
    }

    public void updatePassword(String userId, String passwordHash, String updatedBy) {
        userMapper.update(null, update(userId)
                .set(UserRecord::getPasswordHash, passwordHash)
                .set(UserRecord::getUpdatedBy, updatedBy)
                .set(UserRecord::getUpdatedAt, now()));
    }

    public void updateAdmin(String userId, String email, String avatar, UserStatus status,
                            List<String> roles, List<String> permissions, String updatedBy) {
        userMapper.update(null, update(userId)
                .set(UserRecord::getEmail, emptyToNull(email))
                .set(UserRecord::getAvatar, emptyToNull(avatar))
                .set(UserRecord::getStatus, status)
                .set(UserRecord::getRoles, roles)
                .set(UserRecord::getPermissions, permissions)
                .set(UserRecord::getUpdatedBy, updatedBy)
                .set(UserRecord::getUpdatedAt, now()));
    }

    public void recordLogin(String userId) {
        OffsetDateTime now = now();
        userMapper.update(null, update(userId)
                .set(UserRecord::getLastLoginAt, now)
                .set(UserRecord::getUpdatedBy, userId)
                .set(UserRecord::getUpdatedAt, now));
    }

    public IPage<UserRecord> list(AdminUserListQuery query) {
        return userMapper.selectUserPage(query.toPage(), query);
    }

    public UserStatusSummary summarize(String keyword) {
        return userMapper.selectStatusSummary(keyword);
    }

    private LambdaUpdateWrapper<UserRecord> update(String userId) {
        return new LambdaUpdateWrapper<UserRecord>()
                .eq(UserRecord::getUserId, userId)
                .ne(UserRecord::getStatus, UserStatus.DELETED);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
