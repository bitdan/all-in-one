package com.linger.module.toolhub.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RedissonConfigPlaceholderTest {

    @Test
    void shouldParseEnvironmentAwareConfigs() throws Exception {
        assertConfig("redisson-dev.yml");
        assertConfig("redisson-local.yml");
    }

    private void assertConfig(String resource) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            Config config = Config.fromYAML(input);
            assertNotNull(config);
        }
    }
}
