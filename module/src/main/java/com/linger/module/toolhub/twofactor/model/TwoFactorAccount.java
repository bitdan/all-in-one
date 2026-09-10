package com.linger.module.toolhub.twofactor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorAccount {
    private String id;
    private String label;
    private String issuer;
    private String accountName;
    private String secret;
    private Integer digits;
    private Integer period;
    private TotpAlgorithm algorithm;
    private String createdAt;
    private String updatedAt;
}
