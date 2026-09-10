package com.linger.module.toolhub.twofactor;

import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.config.ToolHubProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class TwoFactorCrypto {

    private static final String PREFIX = "v1:";
    private final ToolHubProperties properties;
    private final SecureRandom random = new SecureRandom();

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("2FA 数据加密失败", exception);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            if (encryptedText == null || !encryptedText.startsWith(PREFIX)) {
                throw BusinessException.badRequest("2FA 数据格式不正确");
            }
            byte[] payload = Base64.getDecoder().decode(encryptedText.substring(PREFIX.length()));
            if (payload.length <= 12) throw BusinessException.badRequest("2FA 数据格式不正确");
            byte[] iv = Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("2FA 数据解密失败，请检查 TOOL_HUB_TOTP_MASTER_KEY", exception);
        }
    }

    private SecretKeySpec key() throws Exception {
        String masterKey = properties.getTotpMasterKey();
        if (masterKey == null || masterKey.trim().length() < 16) {
            throw BusinessException.unavailable("请配置至少16位的 TOOL_HUB_TOTP_MASTER_KEY");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(masterKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(Arrays.copyOf(digest, 16), "AES");
    }
}
