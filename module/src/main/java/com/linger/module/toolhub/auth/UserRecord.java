package com.linger.module.toolhub.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.linger.module.common.persistence.BaseAuditEntity;
import com.linger.module.toolhub.auth.handler.StringListJsonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_users", autoResultMap = true)
public class UserRecord extends BaseAuditEntity {
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;
    private String username;
    private String passwordHash;
    private String email;
    private String avatar;
    private String status;
    @TableField(typeHandler = StringListJsonTypeHandler.class)
    private List<String> roles = new ArrayList<>();
    @TableField(typeHandler = StringListJsonTypeHandler.class)
    private List<String> permissions = new ArrayList<>();
    private OffsetDateTime lastLoginAt;
}
