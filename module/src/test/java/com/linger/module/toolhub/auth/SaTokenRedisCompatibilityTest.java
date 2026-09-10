package com.linger.module.toolhub.auth;

import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Slf4j
class SaTokenRedisCompatibilityTest {

    @Test
    void shouldUpdateSessionWithExistingPositiveTtl() {
        String key = "Authorization:session:test";
        String value = "session-value";
        long ttlMillis = 2_000L;
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).thenReturn(ttlMillis);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SaTokenDaoForRedisTemplate dao = new SaTokenDaoForRedisTemplate();
        dao.stringRedisTemplate = redisTemplate;
        dao.update(key, value);

        verify(valueOperations).set(key, value, ttlMillis, TimeUnit.MILLISECONDS);
        log.info("Sa-Token Session 更新保留原 TTL：key={}, ttlMillis={}", key, ttlMillis);
    }
}
