package com.linger.module.toolhub.twofactor;

import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.config.ToolHubProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoFactorCryptoTest {

    @Test
    void shouldEncryptWithRandomIvAndDecrypt() {
        ToolHubProperties properties = new ToolHubProperties();
        properties.setTotpMasterKey("test-master-key-with-32-characters");
        TwoFactorCrypto crypto = new TwoFactorCrypto(properties);

        String first = crypto.encrypt("[{\"secret\":\"ABC\"}]");
        String second = crypto.encrypt("[{\"secret\":\"ABC\"}]");

        assertTrue(first.startsWith("v1:"));
        assertNotEquals(first, second);
        assertEquals("[{\"secret\":\"ABC\"}]", crypto.decrypt(first));
        assertEquals("[{\"secret\":\"ABC\"}]", crypto.decrypt(second));
    }

    @Test
    void shouldRequireConfiguredMasterKey() {
        TwoFactorCrypto crypto = new TwoFactorCrypto(new ToolHubProperties());
        assertThrows(BusinessException.class, () -> crypto.encrypt("[]"));
    }
}
