package com.linger.module.toolhub.auth;

import cn.dev33.satoken.stp.StpInterface;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@AllArgsConstructor
public class ToolHubStpInterface implements StpInterface {

    private final UserRepository userRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return userRepository.findById(String.valueOf(loginId))
                .map(UserRecord::getPermissions)
                .orElse(Collections.emptyList());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userRepository.findById(String.valueOf(loginId))
                .map(UserRecord::getRoles)
                .orElse(Collections.emptyList());
    }
}
