package com.linger.module.toolhub.twofactor;

import com.linger.module.common.ApiResponse;
import com.linger.module.toolhub.auth.AuthService;
import com.linger.module.toolhub.twofactor.dto.TwoFactorAccountResponse;
import com.linger.module.toolhub.twofactor.dto.TwoFactorAccountRequest;
import com.linger.module.toolhub.twofactor.dto.TwoFactorExportResponse;
import com.linger.module.toolhub.twofactor.dto.TwoFactorImportRequest;
import com.linger.module.toolhub.twofactor.dto.TwoFactorImportResponse;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/2fa/accounts")
@AllArgsConstructor
public class TwoFactorController {

    private final AuthService authService;
    private final TwoFactorService twoFactorService;

    @GetMapping
    public ApiResponse<List<TwoFactorAccountResponse>> list() {
        return ApiResponse.success("获取 2FA 账号成功", twoFactorService.list(authService.currentUserId(), false));
    }

    @GetMapping("/export")
    public ApiResponse<TwoFactorExportResponse<?>> export(@RequestParam(defaultValue = "json") String exportFormat) {
        return ApiResponse.success("导出 2FA 账号成功", twoFactorService.export(authService.currentUserId(), exportFormat));
    }

    @GetMapping("/{accountId}")
    public ApiResponse<TwoFactorAccountResponse> get(@PathVariable String accountId) {
        return ApiResponse.success("获取 2FA 账号成功", twoFactorService.get(authService.currentUserId(), accountId));
    }

    @PostMapping
    public ApiResponse<TwoFactorAccountResponse> create(@RequestBody TwoFactorAccountRequest request) {
        return ApiResponse.success("创建 2FA 账号成功", twoFactorService.create(authService.currentUserId(), request));
    }

    @PutMapping("/{accountId}")
    public ApiResponse<TwoFactorAccountResponse> update(@PathVariable String accountId,
                                                         @RequestBody TwoFactorAccountRequest request) {
        return ApiResponse.success("更新 2FA 账号成功",
                twoFactorService.update(authService.currentUserId(), accountId, request));
    }

    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> delete(@PathVariable String accountId) {
        twoFactorService.delete(authService.currentUserId(), accountId);
        return ApiResponse.success("删除 2FA 账号成功");
    }

    @PostMapping("/import")
    public ApiResponse<TwoFactorImportResponse> importAccounts(@RequestBody TwoFactorImportRequest request) {
        return ApiResponse.success("导入 2FA 账号成功",
                twoFactorService.importAccounts(authService.currentUserId(), request));
    }
}
