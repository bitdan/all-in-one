package com.linger.module.toolhub.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.linger.module.common.ApiResponse;
import com.linger.module.toolhub.auth.dto.AdminPasswordResetRequest;
import com.linger.module.toolhub.auth.dto.AdminUserPageResponse;
import com.linger.module.toolhub.auth.dto.AdminUserResponse;
import com.linger.module.toolhub.auth.dto.AdminUserListQuery;
import com.linger.module.toolhub.auth.dto.AdminUserUpdateRequest;
import com.linger.module.toolhub.auth.dto.ChangePasswordRequest;
import com.linger.module.toolhub.auth.dto.CaptchaResponse;
import com.linger.module.toolhub.auth.dto.LoginRequest;
import com.linger.module.toolhub.auth.dto.LoginStatsResponse;
import com.linger.module.toolhub.auth.dto.RegisterRequest;
import com.linger.module.toolhub.auth.dto.TokenResponse;
import com.linger.module.toolhub.auth.dto.UpdateProfileRequest;
import com.linger.module.toolhub.auth.dto.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTH_COOKIE = "tool_hub_token";

    private final AuthService authService;
    private final CaptchaService captchaService;

    @Value("${sa-token.timeout:21600}")
    private int timeout;

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@RequestBody RegisterRequest request,
                                                HttpServletResponse response) {
        String token = authService.register(request);
        setCookie(response, token);
        return ApiResponse.success("注册成功", tokenData(token));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        String token = authService.login(request);
        setCookie(response, token);
        return ApiResponse.success("登录成功", tokenData(token));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletResponse response) {
        StpUtil.checkLogin();
        StpUtil.logout();
        Cookie cookie = new Cookie(AUTH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ApiResponse.success("登出成功");
    }

    @GetMapping("/captchaImage")
    public ApiResponse<CaptchaResponse> captcha() {
        return ApiResponse.success("获取验证码成功", captchaService.create());
    }

    @GetMapping("/getInfo")
    public ApiResponse<UserInfoResponse> info() {
        return ApiResponse.success("获取用户信息成功", authService.userInfo(authService.currentUser()));
    }

    @GetMapping("/profile/login-stats")
    public ApiResponse<LoginStatsResponse> loginStats() {
        return ApiResponse.success("获取登录统计成功", authService.loginStats());
    }

    @PutMapping("/profile")
    public ApiResponse<UserInfoResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        return ApiResponse.success("更新个人信息成功", authService.updateProfile(request));
    }

    @PutMapping("/profile/password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ApiResponse.success("修改密码成功");
    }

    @GetMapping("/admin/users")
    public ApiResponse<AdminUserPageResponse> users(@ModelAttribute AdminUserListQuery query) {
        return ApiResponse.success("获取用户列表成功", authService.listUsers(query));
    }

    @PutMapping("/admin/users/{userId}")
    public ApiResponse<AdminUserResponse> updateUser(@PathVariable String userId,
                                                      @RequestBody AdminUserUpdateRequest request) {
        return ApiResponse.success("更新用户成功", authService.updateAdmin(userId, request));
    }

    @PutMapping("/admin/users/{userId}/password")
    public ApiResponse<Void> resetPassword(@PathVariable String userId,
                                            @RequestBody AdminPasswordResetRequest request) {
        authService.resetPassword(userId, request);
        return ApiResponse.success("重置密码成功");
    }

    private TokenResponse tokenData(String token) {
        return new TokenResponse(token);
    }

    private void setCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(AUTH_COOKIE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(timeout);
        response.addCookie(cookie);
    }
}
