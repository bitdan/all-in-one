package com.linger.module.toolhub.twofactor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linger.module.toolhub.twofactor.dto.TwoFactorAccountRequest;
import com.linger.module.toolhub.twofactor.dto.TwoFactorImportRequest;
import com.linger.module.totp.TotpNative;
import com.linger.module.exception.BusinessException;
import com.linger.module.util.JsonUtils;
import lombok.AllArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@AllArgsConstructor
public class TwoFactorService {

    private final RedissonClient redissonClient;
    private final TwoFactorCrypto crypto;

    public List<Map<String, Object>> list(String userId, boolean includeSecret) {
        long now = System.currentTimeMillis() / 1000L;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> account : load(userId)) {
            result.add(view(account, now, includeSecret));
        }
        return result;
    }

    public Map<String, Object> get(String userId, String accountId) {
        for (Map<String, Object> account : load(userId)) {
            if (accountId.equals(string(account.get("id")))) return view(account, now(), true);
        }
        throw BusinessException.notFound("账号不存在");
    }

    public Map<String, Object> create(String userId, TwoFactorAccountRequest request) {
        Map<String, Object> account = normalize(request.toMap(), null);
        locked(userId, () -> {
            List<Map<String, Object>> accounts = load(userId);
            accounts.add(0, account);
            save(userId, accounts);
            return null;
        });
        return view(account, now(), true);
    }

    public Map<String, Object> update(String userId, String accountId, TwoFactorAccountRequest request) {
        return locked(userId, () -> {
            List<Map<String, Object>> accounts = load(userId);
            for (int index = 0; index < accounts.size(); index++) {
                Map<String, Object> current = accounts.get(index);
                if (accountId.equals(string(current.get("id")))) {
                    Map<String, Object> merged = new LinkedHashMap<>(current);
                    merged.putAll(request.toMap());
                    Map<String, Object> next = normalize(merged, accountId);
                    next.put("createdAt", current.get("createdAt"));
                    accounts.set(index, next);
                    save(userId, accounts);
                    return view(next, now(), true);
                }
            }
            throw BusinessException.notFound("账号不存在");
        });
    }

    public void delete(String userId, String accountId) {
        locked(userId, () -> {
            List<Map<String, Object>> accounts = load(userId);
            boolean removed = accounts.removeIf(item -> accountId.equals(string(item.get("id"))));
            if (!removed) throw BusinessException.notFound("账号不存在");
            save(userId, accounts);
            return null;
        });
    }

    public Map<String, Object> importAccounts(String userId, TwoFactorImportRequest request) {
        List<Map<String, Object>> parsed = new ArrayList<>();
        String text = request.getText();
        if (text != null && !text.trim().isEmpty()) parsed.addAll(parseImportText(text));
        if (request.getItems() != null) {
            for (TwoFactorAccountRequest item : request.getItems()) {
                if (item != null) parsed.add(normalize(item.toMap(), null));
            }
        }
        if (parsed.isEmpty()) throw BusinessException.badRequest("没有可导入的账号");

        List<Map<String, Object>> next = locked(userId, () -> {
            List<Map<String, Object>> updated = "replace".equals(request.getMergeMode())
                    ? new ArrayList<>() : load(userId);
            Set<String> signatures = new LinkedHashSet<>();
            for (Map<String, Object> item : updated) signatures.add(signature(item));
            for (Map<String, Object> item : parsed) {
                if (signatures.add(signature(item))) updated.add(item);
            }
            save(userId, updated);
            return updated;
        });
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", parsed.size());
        result.put("total", next.size());
        List<Map<String, Object>> views = new ArrayList<>();
        for (Map<String, Object> item : parsed) views.add(view(item, now(), true));
        result.put("items", views);
        return result;
    }

    public Map<String, Object> export(String userId, String format) {
        List<Map<String, Object>> accounts = load(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        if ("otpauth".equalsIgnoreCase(format)) {
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> account : accounts) lines.add(buildUri(account));
            result.put("format", "otpauth");
            result.put("content", String.join("\n", lines));
            return result;
        }
        if (!"json".equalsIgnoreCase(format)) throw BusinessException.badRequest("不支持的导出格式");
        List<Map<String, Object>> views = new ArrayList<>();
        for (Map<String, Object> account : accounts) views.add(view(account, now(), true));
        result.put("format", "json");
        result.put("content", views);
        return result;
    }

    private List<Map<String, Object>> parseImportText(String raw) {
        try {
            String text = raw.trim();
            if (text.startsWith("[")) {
                List<Map<String, Object>> items = JsonUtils.parseObject(text,
                        new TypeReference<List<Map<String, Object>>>() { });
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> item : items) result.add(normalize(item, null));
                return result;
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (String line : text.split("\\r?\\n")) {
                if (line.trim().isEmpty()) continue;
                if (line.trim().startsWith("otpauth-migration://")) {
                    result.addAll(parseMigrationUri(line.trim()));
                } else {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("otpauthUri", line.trim());
                    result.add(normalize(payload, null));
                }
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest("导入内容格式不正确");
        }
    }

    private Map<String, Object> normalize(Map<String, Object> payload, String existingId) {
        Map<String, Object> source = payload;
        String uri = string(payload.get("otpauthUri"));
        if (uri != null && !uri.trim().isEmpty()) {
            if (uri.startsWith("otpauth-migration://")) {
                List<Map<String, Object>> items = parseMigrationUri(uri);
                if (items.size() != 1) throw BusinessException.badRequest("Google 导出二维码包含多个账号，请使用导入功能");
                source = items.get(0);
            } else {
                source = parseUri(uri);
            }
        }
        String secret = normalizeSecret(string(source.get("secret")));
        String issuer = defaultString(string(source.get("issuer")), "Tool Hub");
        String accountName = defaultString(string(source.get("accountName")), string(source.get("account_name")));
        accountName = defaultString(accountName, "");
        String label = defaultString(string(source.get("label")), accountName.isEmpty() ? issuer : issuer + " (" + accountName + ")");
        String algorithm = defaultString(string(source.get("algorithm")), "SHA1").toUpperCase(Locale.ROOT).replace("-", "");
        if (!java.util.Arrays.asList("SHA1", "SHA256", "SHA512").contains(algorithm)) {
            throw BusinessException.badRequest("仅支持 SHA1 / SHA256 / SHA512");
        }
        int digits = integer(source.get("digits"), 6);
        if (digits != 6 && digits != 8) throw BusinessException.badRequest("digits 仅支持 6 或 8");
        int period = integer(source.get("period"), 30);
        if (period <= 0) throw BusinessException.badRequest("period 必须大于 0");
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", existingId == null ? "totp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) : existingId);
        account.put("label", label);
        account.put("issuer", issuer);
        account.put("accountName", accountName);
        account.put("secret", secret);
        account.put("digits", digits);
        account.put("period", period);
        account.put("algorithm", algorithm);
        account.put("createdAt", now);
        account.put("updatedAt", now);
        return account;
    }

    private Map<String, Object> parseUri(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"otpauth".equalsIgnoreCase(uri.getScheme()) || !"totp".equalsIgnoreCase(uri.getHost())) {
                throw BusinessException.badRequest("仅支持 otpauth://totp/ 链接");
            }
            String label = decode(uri.getRawPath() == null ? "" : uri.getRawPath().replaceFirst("^/", ""));
            String issuerFromLabel = "";
            String accountName = label;
            int separator = label.indexOf(':');
            if (separator >= 0) {
                issuerFromLabel = label.substring(0, separator).trim();
                accountName = label.substring(separator + 1).trim();
            }
            Map<String, String> query = query(uri.getRawQuery());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("label", label);
            result.put("issuer", defaultString(query.get("issuer"), issuerFromLabel));
            result.put("accountName", accountName);
            result.put("secret", query.get("secret"));
            result.put("digits", query.get("digits"));
            result.put("period", query.get("period"));
            result.put("algorithm", query.get("algorithm"));
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest("otpauth 链接格式不正确");
        }
    }

    private List<Map<String, Object>> parseMigrationUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!"otpauth-migration".equalsIgnoreCase(uri.getScheme()) || !"offline".equalsIgnoreCase(uri.getHost())) {
                throw BusinessException.badRequest("Google 导出二维码格式不正确");
            }
            String data = query(uri.getRawQuery()).get("data");
            if (data == null) throw BusinessException.badRequest("Google 导出二维码缺少 data 参数");
            byte[] bytes = Base64.getUrlDecoder().decode(padBase64(data));
            List<ProtoField> migration = proto(bytes);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ProtoField field : migration) {
                if (field.number == 1 && field.bytes != null) {
                    Map<String, Object> item = migrationAccount(proto(field.bytes));
                    if (item != null) result.add(normalize(item, null));
                }
            }
            if (result.isEmpty()) throw BusinessException.badRequest("未解析到 TOTP 账号");
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest("Google 导出二维码格式不正确");
        }
    }

    private Map<String, Object> migrationAccount(List<ProtoField> fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        int type = 2;
        for (ProtoField field : fields) {
            if (field.number == 1 && field.bytes != null) result.put("secret", base32(field.bytes));
            if (field.number == 2 && field.bytes != null) result.put("accountName", new String(field.bytes, StandardCharsets.UTF_8));
            if (field.number == 3 && field.bytes != null) result.put("issuer", new String(field.bytes, StandardCharsets.UTF_8));
            if (field.number == 4) result.put("algorithm", field.value == 2 ? "SHA256" : field.value == 3 ? "SHA512" : "SHA1");
            if (field.number == 5) result.put("digits", field.value == 2 ? 8 : 6);
            if (field.number == 6) type = (int) field.value;
        }
        if (type != 2) return null;
        result.put("period", 30);
        return result;
    }

    private List<ProtoField> proto(byte[] bytes) {
        List<ProtoField> fields = new ArrayList<>();
        int[] index = {0};
        while (index[0] < bytes.length) {
            long key = varint(bytes, index);
            int number = (int) (key >> 3);
            int wireType = (int) (key & 7);
            if (wireType == 0) fields.add(new ProtoField(number, varint(bytes, index), null));
            else if (wireType == 2) {
                int length = (int) varint(bytes, index);
                if (length < 0 || index[0] + length > bytes.length) throw BusinessException.badRequest("Google 导出数据损坏");
                byte[] part = java.util.Arrays.copyOfRange(bytes, index[0], index[0] + length);
                index[0] += length;
                fields.add(new ProtoField(number, 0, part));
            } else throw BusinessException.badRequest("Google 导出字段类型不支持");
        }
        return fields;
    }

    private long varint(byte[] bytes, int[] index) {
        long value = 0;
        int shift = 0;
        while (index[0] < bytes.length && shift < 64) {
            int current = bytes[index[0]++] & 255;
            value |= (long) (current & 127) << shift;
            if ((current & 128) == 0) return value;
            shift += 7;
        }
        throw BusinessException.badRequest("Google 导出数据损坏");
    }

    private Map<String, Object> view(Map<String, Object> account, long now, boolean includeSecret) {
        int period = integer(account.get("period"), 30);
        int digits = integer(account.get("digits"), 6);
        String secret = string(account.get("secret"));
        String code;
        try {
            code = TotpNative.generateTotpAtTime(secret, now / period, string(account.get("algorithm")), digits);
        } catch (Exception exception) {
            throw new IllegalStateException("TOTP 生成失败", exception);
        }
        Map<String, Object> result = new LinkedHashMap<>(account);
        result.remove("secret");
        result.put("code", code);
        result.put("secondsRemaining", period - now % period);
        result.put("secretMasked", mask(secret));
        if (includeSecret) {
            result.put("secret", secret);
            result.put("otpauthUri", buildUri(account));
        }
        return result;
    }

    private String buildUri(Map<String, Object> account) {
        String issuer = string(account.get("issuer"));
        String accountName = string(account.get("accountName"));
        String label = accountName == null || accountName.isEmpty() ? string(account.get("label")) : issuer + ":" + accountName;
        return "otpauth://totp/" + encode(label) + "?secret=" + string(account.get("secret")) +
                "&issuer=" + encode(issuer) + "&algorithm=" + string(account.get("algorithm")) +
                "&digits=" + account.get("digits") + "&period=" + account.get("period");
    }

    private List<Map<String, Object>> load(String userId) {
        RBucket<String> bucket = redissonClient.getBucket(key(userId), StringCodec.INSTANCE);
        String value = bucket.get();
        if (value == null || value.isEmpty()) return new ArrayList<>();
        try {
            boolean legacyPlainText = !value.startsWith("v1:");
            String json = legacyPlainText ? value : crypto.decrypt(value);
            List<Map<String, Object>> accounts = JsonUtils.parseObject(json,
                    new TypeReference<List<Map<String, Object>>>() { });
            if (legacyPlainText) {
                // 首次由 Java 读取 Python 遗留明文时原位升级，后续只保存 AES-GCM 密文。
                bucket.compareAndSet(value, crypto.encrypt(json));
            }
            return accounts;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("2FA 数据读取失败", exception);
        }
    }

    private void save(String userId, List<Map<String, Object>> accounts) {
        try {
            String json = JsonUtils.toJsonString(accounts);
            redissonClient.getBucket(key(userId), StringCodec.INSTANCE).set(crypto.encrypt(json));
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("2FA 数据保存失败", exception);
        }
    }

    private <T> T locked(String userId, Supplier<T> action) {
        RLock lock = redissonClient.getLock("toolhub:2fa:lock:" + userId);
        lock.lock(10, TimeUnit.SECONDS);
        try {
            return action.get();
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private String normalizeSecret(String secret) {
        if (secret == null) throw BusinessException.badRequest("secret 不能为空");
        String normalized = secret.replaceAll("\\s+", "").replaceAll("=+$", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) throw BusinessException.badRequest("secret 不能为空");
        try {
            TotpNative.base32Decode(normalized);
            return normalized;
        } catch (Exception exception) {
            throw BusinessException.badRequest("secret 不是合法的 Base32");
        }
    }

    private Map<String, String> query(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null) return result;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(decode(parts[0]), parts.length > 1 ? decode(parts[1]) : "");
        }
        return result;
    }

    private String base32(byte[] data) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) {
                result.append(alphabet.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) result.append(alphabet.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    private String padBase64(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return value + String.join("", Collections.nCopies(padding, "="));
    }

    private String signature(Map<String, Object> item) {
        return string(item.get("issuer")) + "|" + string(item.get("accountName")) + "|" + string(item.get("secret")) +
                "|" + item.get("digits") + "|" + item.get("period") + "|" + item.get("algorithm");
    }

    private String mask(String secret) {
        if (secret.length() <= 8) return secret;
        return secret.substring(0, 4) + String.join("", Collections.nCopies(secret.length() - 8, "*")) + secret.substring(secret.length() - 4);
    }

    private int integer(Object value, int defaultValue) {
        if (value == null || string(value).trim().isEmpty()) return defaultValue;
        try { return Integer.parseInt(string(value)); }
        catch (NumberFormatException exception) { throw BusinessException.badRequest("数字参数格式不正确"); }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception exception) {
            throw new IllegalStateException("UTF-8 编码不可用", exception);
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            throw new IllegalStateException("UTF-8 解码不可用", exception);
        }
    }
    private long now() { return System.currentTimeMillis() / 1000L; }
    private String key(String userId) { return "totp:accounts:" + userId; }

    private static class ProtoField {
        private final int number;
        private final long value;
        private final byte[] bytes;
        private ProtoField(int number, long value, byte[] bytes) {
            this.number = number;
            this.value = value;
            this.bytes = bytes;
        }
    }
}
