package com.linger.module.totp;

/**
 * @version 1.0
 * @description TotpNative
 * @date 2025/7/30 15:47:04
 */

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;

public class TotpNative {

    // Base32实现（RFC 4648标准）
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] BASE32_DECODE = new int[256];

    static {
        for (int i = 0; i < BASE32_DECODE.length; i++) {
            BASE32_DECODE[i] = -1;
        }
        for (int i = 0; i < BASE32_CHARS.length(); i++) {
            BASE32_DECODE[BASE32_CHARS.charAt(i)] = i;
        }
    }

    // Base32解码
    public static byte[] base32Decode(String secret) {
        secret = secret.trim().replaceAll("=", "").toUpperCase(Locale.ROOT);
        int remainder = secret.length() % 8;
        if (remainder == 1 || remainder == 3 || remainder == 6) {
            throw new IllegalArgumentException("非法Base32长度");
        }
        int buffer = 0;
        int bitsLeft = 0;
        int byteCount = (secret.length() * 5) / 8;
        byte[] result = new byte[byteCount];
        int index = 0;

        for (char c : secret.toCharArray()) {
            if (c >= BASE32_DECODE.length) {
                throw new IllegalArgumentException("非法Base32字符: " + c);
            }
            int val = BASE32_DECODE[c];
            if (val < 0) {
                throw new IllegalArgumentException("非法Base32字符: " + c);
            }
            buffer <<= 5;
            buffer |= val & 0xFF;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }

    // 生成TOTP核心方法
    public static String generateTotp(String secretKey) throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] keyBytes = base32Decode(secretKey);
        long timeStep = Instant.now().getEpochSecond() / 30;

        // HMAC-SHA1计算
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timeStep).array());

        // 动态截取（RFC4226标准）
        int offset = hash[hash.length - 1] & 0xF;
        int binary = ((hash[offset] & 0x7F) << 24) |
                ((hash[offset + 1] & 0xFF) << 16) |
                ((hash[offset + 2] & 0xFF) << 8) |
                (hash[offset + 3] & 0xFF);

        // 生成6位数字
        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
    }

    // 新增：根据指定时间步生成TOTP码
    public static String generateTotpAtTime(String secretKey, long timeStep) throws NoSuchAlgorithmException, InvalidKeyException {
        return generateTotpAtTime(secretKey, timeStep, "SHA1", 6);
    }

    public static String generateTotpAtTime(String secretKey, long timeStep, String algorithm, int digits)
            throws NoSuchAlgorithmException, InvalidKeyException {
        byte[] keyBytes = base32Decode(secretKey);
        String macAlgorithm = "Hmac" + algorithm.replace("-", "").toUpperCase(Locale.ROOT);
        Mac mac = Mac.getInstance(macAlgorithm);
        mac.init(new SecretKeySpec(keyBytes, macAlgorithm));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timeStep).array());

        int offset = hash[hash.length - 1] & 0xF;
        int binary = ((hash[offset] & 0x7F) << 24) |
                ((hash[offset + 1] & 0xFF) << 16) |
                ((hash[offset + 2] & 0xFF) << 8) |
                (hash[offset + 3] & 0xFF);

        int divisor = 1;
        for (int index = 0; index < digits; index++) {
            divisor *= 10;
        }
        int otp = binary % divisor;
        return String.format("%0" + digits + "d", otp);
    }


    // 生成随机Base32密钥
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        new java.security.SecureRandom().nextBytes(bytes);
        return bytesToBase32(bytes);
    }

    private static String bytesToBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;

            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(BASE32_CHARS.charAt(index));
                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_CHARS.charAt(index));
        }
        return result.toString();
    }

}
