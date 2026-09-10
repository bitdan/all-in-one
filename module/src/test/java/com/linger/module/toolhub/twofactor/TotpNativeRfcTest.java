package com.linger.module.toolhub.twofactor;

import com.linger.module.totp.TotpNative;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TotpNativeRfcTest {

    @Test
    void shouldMatchRfc6238VectorsForEverySupportedAlgorithm() throws Exception {
        assertVector(59L, "94287082", "46119246", "90693936");
        assertVector(1111111109L, "07081804", "68084774", "25091201");
        assertVector(20000000000L, "65353130", "77737706", "47863826");
    }

    @Test
    void shouldRejectNonBase32Characters() {
        assertThrows(IllegalArgumentException.class, () -> TotpNative.base32Decode("中文"));
        assertThrows(IllegalArgumentException.class, () -> TotpNative.base32Decode("A"));
    }

    private void assertVector(long epochSeconds, String sha1, String sha256, String sha512) throws Exception {
        long counter = epochSeconds / 30L;
        assertEquals(sha1, TotpNative.generateTotpAtTime(base32("12345678901234567890"), counter, "SHA1", 8));
        assertEquals(sha256, TotpNative.generateTotpAtTime(base32("12345678901234567890123456789012"), counter, "SHA256", 8));
        assertEquals(sha512, TotpNative.generateTotpAtTime(base32("1234567890123456789012345678901234567890123456789012345678901234"), counter, "SHA512", 8));
    }

    private String base32(String value) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte item : value.getBytes(StandardCharsets.US_ASCII)) {
            buffer = (buffer << 8) | (item & 255);
            bits += 8;
            while (bits >= 5) {
                result.append(alphabet.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) result.append(alphabet.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }
}
