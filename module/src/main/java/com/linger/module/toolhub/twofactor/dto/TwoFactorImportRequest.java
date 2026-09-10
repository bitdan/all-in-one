package com.linger.module.toolhub.twofactor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TwoFactorImportRequest {

    private String text;
    private List<TwoFactorAccountRequest> items = new ArrayList<>();
    private String mergeMode;
}
