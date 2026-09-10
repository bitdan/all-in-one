package com.linger.module.toolhub.twofactor.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.linger.module.toolhub.twofactor.model.TotpAlgorithm;
import lombok.Data;

@Data
public class TwoFactorAccountRequest {

    private String label;
    private String issuer;
    @JsonAlias("account_name")
    private String accountName;
    private String secret;
    private Integer digits;
    private Integer period;
    private TotpAlgorithm algorithm;
    @JsonAlias("otpauth_uri")
    private String otpauthUri;
}
