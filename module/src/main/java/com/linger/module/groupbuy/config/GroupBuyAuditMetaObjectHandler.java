package com.linger.module.groupbuy.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 拼团实体审计字段兜底填充器。
 *
 * <p>当前模块还未接入统一认证上下文，因此无法识别操作者的后台任务使用 0 表示系统。
 * 下单、开团等已经持有用户 ID 的链路会在构建实体时显式赋值，严格填充不会覆盖已有值。</p>
 */
@Component
public class GroupBuyAuditMetaObjectHandler implements MetaObjectHandler {

    private static final Long SYSTEM_USER_ID = 0L;

    @Override
    public void insertFill(MetaObject metaObject) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        strictInsertFill(metaObject, "createdBy", Long.class, SYSTEM_USER_ID);
        strictInsertFill(metaObject, "updatedBy", Long.class, SYSTEM_USER_ID);
        strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedBy", Long.class, SYSTEM_USER_ID);
        strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
