package com.linger.module.toolhub.twofactor.dto;

import lombok.Value;

import java.util.List;

@Value
public class TwoFactorImportResponse {
    Integer imported;
    Integer total;
    List<TwoFactorAccountResponse> items;
}
