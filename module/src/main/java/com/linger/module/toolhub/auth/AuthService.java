package com.linger.module.toolhub.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.auth.dto.AdminPasswordResetRequest;
import com.linger.module.toolhub.auth.dto.AdminUserListQuery;
import com.linger.module.toolhub.auth.dto.AdminUserPageResponse;
import com.linger.module.toolhub.auth.dto.AdminUserResponse;
import com.linger.module.toolhub.auth.dto.AdminUserUpdateRequest;
import com.linger.module.toolhub.auth.dto.ChangePasswordRequest;
import com.linger.module.toolhub.auth.dto.LoginRequest;
import com.linger.module.toolhub.auth.dto.LoginStatsDayResponse;
import com.linger.module.toolhub.auth.dto.LoginStatsResponse;
import com.linger.module.toolhub.auth.dto.RegisterRequest;
import com.linger.module.toolhub.auth.dto.UpdateProfileRequest;
import com.linger.module.toolhub.auth.dto.UserInfoResponse;
import com.linger.module.toolhub.auth.dto.UserProfileResponse;
import com.linger.module.toolhub.auth.dto.UserStatusSummary;
import com.linger.module.toolhub.auth.model.UserStatus;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CaptchaService captchaService;
    private final RedissonClient redissonClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String register(RegisterRequest request) {
        captchaService.validate(request.getUuid(), request.getCode());
        String username = required(request.getUsername(), "用户名不能为空");
        String password = required(request.getPassword(), "密码不能为空");
        String confirmPassword = required(request.getConfirmPassword(), "确认密码不能为空");
        if (!password.equals(confirmPassword)) {
            throw BusinessException.badRequest("两次输入的密码不一致");
        }
        if (password.length() < 6) {
            throw BusinessException.badRequest("密码长度至少6位");
        }
        if (userRepository.existsUsername(username)) {
            throw BusinessException.badRequest("用户名已存在");
        }
        UserRecord user = new UserRecord();
        user.setUserId("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(emptyToNull(request.getEmail()));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Collections.singletonList("user"));
        user.setPermissions(Collections.emptyList());
        userRepository.insert(user);
        return loginSession(user);
    }

    public String login(LoginRequest request) {
        captchaService.validate(request.getUuid(), request.getCode());
        String username = required(request.getUsername(), "用户名不能为空");
        String password = required(request.getPassword(), "密码不能为空");
        UserRecord user = userRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw BusinessException.unauthorized(user.getStatus() == UserStatus.FROZEN ? "账号已被冻结" : "账号已被禁用");
        }
        return loginSession(user);
    }

    public UserRecord currentUser() {
        StpUtil.checkLogin();
        UserRecord user = getUser(StpUtil.getLoginIdAsString());
        if (user.getStatus() != UserStatus.ACTIVE) {
            StpUtil.logout();
            throw BusinessException.forbidden("账号不可用");
        }
        return user;
    }

    public String currentUserId() {
        StpUtil.checkLogin();
        return StpUtil.getLoginIdAsString();
    }

    public String optionalUserId() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : null;
    }

    public UserRecord getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }

    public UserInfoResponse userInfo(UserRecord user) {
        UserProfileResponse profile = new UserProfileResponse(
                user.getUserId(), user.getUsername(), user.getEmail(), user.getAvatar());
        return new UserInfoResponse(profile, user.getRoles(), user.getPermissions());
    }

    public UserInfoResponse updateProfile(UpdateProfileRequest request) {
        UserRecord user = currentUser();
        userRepository.updateProfile(user.getUserId(), request.getEmail(), request.getAvatar());
        return userInfo(getUser(user.getUserId()));
    }

    public void changePassword(ChangePasswordRequest request) {
        UserRecord user = currentUser();
        String oldPassword = required(request.getOldPassword(), "原密码不能为空");
        String newPassword = required(request.getNewPassword(), "新密码不能为空");
        String confirmPassword = required(request.getConfirmPassword(), "确认密码不能为空");
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw BusinessException.badRequest("原密码错误");
        }
        validateNewPassword(oldPassword, newPassword, confirmPassword);
        userRepository.updatePassword(user.getUserId(), passwordEncoder.encode(newPassword), user.getUserId());
    }

    public AdminUserPageResponse listUsers(AdminUserListQuery query) {
        requireAdmin();
        IPage<UserRecord> page = userRepository.list(query);
        UserStatusSummary summary = userRepository.summarize(query.getKeyword());
        return AdminUserPageResponse.from(page, summary);
    }

    public AdminUserResponse updateAdmin(String userId, AdminUserUpdateRequest request) {
        requireAdmin();
        String updatedBy = currentUserId();
        UserRecord user = getUser(userId);
        UserStatus status = request.getStatus() == null ? user.getStatus() : request.getStatus();
        if (status == UserStatus.DELETED) throw BusinessException.badRequest("不能将用户状态修改为 deleted");
        List<String> roles = request.getRoles() == null ? user.getRoles() : stringList(request.getRoles());
        List<String> permissions = request.getPermissions() == null
                ? user.getPermissions() : stringList(request.getPermissions());
        if (roles.isEmpty()) {
            throw BusinessException.badRequest("角色不能为空");
        }
        String email = request.getEmail() == null ? user.getEmail() : request.getEmail();
        String avatar = request.getAvatar() == null ? user.getAvatar() : request.getAvatar();
        userRepository.updateAdmin(userId, email, avatar, status, roles, permissions, updatedBy);
        if (status != UserStatus.ACTIVE) {
            StpUtil.logout(userId);
        }
        return AdminUserResponse.from(getUser(userId));
    }

    public void resetPassword(String userId, AdminPasswordResetRequest request) {
        requireAdmin();
        String updatedBy = currentUserId();
        getUser(userId);
        String password = required(request.getNewPassword(), "新密码不能为空");
        String confirm = required(request.getConfirmPassword(), "确认密码不能为空");
        validateNewPassword(null, password, confirm);
        userRepository.updatePassword(userId, passwordEncoder.encode(password), updatedBy);
        StpUtil.logout(userId);
    }

    public LoginStatsResponse loginStats() {
        String userId = currentUserId();
        LocalDate today = LocalDate.now();
        RBitSet bitSet = redissonClient.getBitSet(loginBitmapKey(userId, today.getYear()));
        List<LoginStatsDayResponse> recentDays = new ArrayList<>();
        int recentCount = 0;
        for (int offset = 29; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            boolean logged = redissonClient.getBitSet(loginBitmapKey(userId, day.getYear())).get(day.getDayOfYear() - 1L);
            if (logged) recentCount++;
            recentDays.add(new LoginStatsDayResponse(day, logged));
        }
        int monthCount = 0;
        for (int day = 1; day <= today.getDayOfMonth(); day++) {
            if (bitSet.get(today.withDayOfMonth(day).getDayOfYear() - 1L)) monthCount++;
        }
        int consecutive = 0;
        LocalDate cursor = today;
        while (consecutive < 366 && redissonClient.getBitSet(loginBitmapKey(userId, cursor.getYear())).get(cursor.getDayOfYear() - 1L)) {
            consecutive++;
            cursor = cursor.minusDays(1);
        }
        return new LoginStatsResponse(
                today,
                bitSet.get(today.getDayOfYear() - 1L),
                bitSet.cardinality(),
                monthCount,
                recentCount,
                consecutive,
                recentDays);
    }

    public String loginIdByToken(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        Object loginId = StpUtil.getLoginIdByToken(token.trim());
        if (loginId == null) return null;
        UserRecord user = userRepository.findById(String.valueOf(loginId)).orElse(null);
        return user != null && user.getStatus() == UserStatus.ACTIVE ? user.getUserId() : null;
    }

    public void requireAdmin() {
        UserRecord user = currentUser();
        if (!user.getRoles().contains("admin") && !user.getPermissions().contains("*")) {
            throw BusinessException.forbidden("需要管理员权限");
        }
    }

    private String loginSession(UserRecord user) {
        StpUtil.login(user.getUserId());
        userRepository.recordLogin(user.getUserId());
        RBitSet bitSet = redissonClient.getBitSet(loginBitmapKey(user.getUserId(), LocalDate.now().getYear()));
        bitSet.set(LocalDate.now().getDayOfYear() - 1L);
        return StpUtil.getTokenValue();
    }

    private void validateNewPassword(String oldPassword, String password, String confirm) {
        if (!password.equals(confirm)) throw BusinessException.badRequest("两次输入的新密码不一致");
        if (password.length() < 6) throw BusinessException.badRequest("密码长度至少6位");
        if (oldPassword != null && oldPassword.equals(password)) throw BusinessException.badRequest("新密码不能与原密码相同");
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw BusinessException.badRequest(message);
        return value.trim();
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private List<String> stringList(List<String> value) {
        List<String> result = new ArrayList<>();
        for (String item : value) {
            if (item == null) continue;
            String normalized = item.trim();
            if (!normalized.isEmpty() && !result.contains(normalized)) result.add(normalized);
        }
        return result;
    }

    private String loginBitmapKey(String userId, int year) {
        return "auth:login:bitmap:" + userId + ":" + year;
    }
}
