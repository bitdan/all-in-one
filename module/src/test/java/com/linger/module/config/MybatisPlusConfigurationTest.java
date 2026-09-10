package com.linger.module.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MybatisPlusConfigurationTest {

    @Test
    void shouldCreateMybatisPlusInterceptor() {
        MybatisPlusConfiguration configuration = new MybatisPlusConfiguration();
        assertNotNull(configuration.mybatisPlusInterceptor());
    }
}
