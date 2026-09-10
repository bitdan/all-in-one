package com.linger.module.toolhub.twofactor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.linger.module.toolhub.twofactor.model.TotpAlgorithm;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TwoFactorAccountResponse {
    String id;
    String label;
    String issuer;
    String accountName;
    Integer digits;
    Integer period;
    TotpAlgorithm algorithm;
    String createdAt;
    String updatedAt;
    String code;
    Long secondsRemaining;
    String secretMasked;
    String secret;
    String otpauthUri;
}
