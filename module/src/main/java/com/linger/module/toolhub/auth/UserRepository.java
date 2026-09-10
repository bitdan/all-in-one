package com.linger.module.toolhub.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.linger.module.common.page.PageQuery;
import com.linger.module.toolhub.auth.mapper.UserMapper;
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
                        .ne(UserRecord::getStatus, "deleted")
        ));
    }

    public Optional<UserRecord> findById(String userId) {
        return Optional.ofNullable(userMapper.selectOne(
                Wrappers.lambdaQuery(UserRecord.class)
                        .eq(UserRecord::getUserId, userId)
                        .ne(UserRecord::getStatus, "deleted")
        ));
    }

    public boolean existsUsername(String username) {
        return userMapper.selectCount(
                Wrappers.lambdaQuery(UserRecord.class)
                        .eq(UserRecord::getUsername, username)
                        .ne(UserRecord::getStatus, "deleted")
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

    public void updateAdmin(String userId, String email, String avatar, String status,
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

    public List<UserRecord> list(String keyword, PageQuery pageQuery) {
        LambdaQueryWrapper<UserRecord> wrapper = query(keyword, null)
                .orderByDesc(UserRecord::getCreatedAt)
                .orderByDesc(UserRecord::getUserId);
        return userMapper.selectPage(pageQuery.<UserRecord>toPage(false), wrapper).getRecords();
    }

    public int count(String keyword, String status) {
        return userMapper.selectCount(query(keyword, status)).intValue();
    }

    private LambdaQueryWrapper<UserRecord> query(String keyword, String status) {
        LambdaQueryWrapper<UserRecord> wrapper = Wrappers.lambdaQuery(UserRecord.class);
        wrapper.ne(UserRecord::getStatus, "deleted");
        if (keyword != null && !keyword.trim().isEmpty()) {
            String pattern = "%" + keyword.trim() + "%";
            wrapper.and(nested -> nested
                    .apply("username ILIKE {0}", pattern)
                    .or()
                    .apply("email ILIKE {0}", pattern));
        }
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(UserRecord::getStatus, status);
        }
        return wrapper;
    }

    private LambdaUpdateWrapper<UserRecord> update(String userId) {
        return new LambdaUpdateWrapper<UserRecord>()
                .eq(UserRecord::getUserId, userId)
                .ne(UserRecord::getStatus, "deleted");
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
