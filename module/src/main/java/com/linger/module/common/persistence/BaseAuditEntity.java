package com.linger.module.common.persistence;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public abstract class BaseAuditEntity {

    private String createdBy;
    private String updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
