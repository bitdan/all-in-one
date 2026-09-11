package com.linger.module.redisson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linger.module.redisson.controller.SignInController;
import com.linger.module.redisson.dto.MonthlySignInStatsResponse;
import com.linger.module.redisson.dto.SignInResponse;
import com.linger.module.redisson.service.SignInService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SignInControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void signInKeepsExistingJsonContract() throws Exception {
        SignInService service = mock(SignInService.class);
        when(service.signIn(7L, "2026-09-11")).thenReturn("签到成功！");
        when(service.getConsecutiveSignInDays(7L, "2026-09-11")).thenReturn(3);
        SignInController controller = new SignInController(service);

        SignInResponse response = controller.signIn(7L, "2026-09-11");
        JsonNode json = objectMapper.valueToTree(response);

        assertTrue(json.get("success").asBoolean());
        assertEquals("签到成功！", json.get("message").asText());
        assertEquals(3, json.get("consecutiveDays").asInt());
        assertEquals("2026-09-11", json.get("signDate").asText());
    }

    @Test
    void failureResponseDoesNotExposeInternalExceptionMessage() throws Exception {
        SignInService service = mock(SignInService.class);
        when(service.getMonthlySignInStats(7L, "2026-09"))
                .thenThrow(new IllegalStateException("redis password=secret"));
        SignInController controller = new SignInController(service);

        MonthlySignInStatsResponse response = controller.getMonthlyStats(7L, "2026-09");
        JsonNode json = objectMapper.valueToTree(response);

        assertFalse(json.get("success").asBoolean());
        assertEquals("查询失败", json.get("message").asText());
        assertFalse(json.has("stats"));
        assertFalse(json.toString().contains("password"));
    }
}
