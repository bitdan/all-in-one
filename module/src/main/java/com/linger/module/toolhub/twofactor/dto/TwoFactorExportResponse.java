package com.linger.module.toolhub.twofactor.dto;

import lombok.Value;

@Value
public class TwoFactorExportResponse<T> {
    String format;
    T content;
}
