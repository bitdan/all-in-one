package com.linger.module.toolhub.twofactor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.twofactor.dto.TwoFactorAccountResponse;
import com.linger.module.toolhub.twofactor.dto.TwoFactorAccountRequest;
import com.linger.module.toolhub.twofactor.dto.TwoFactorExportResponse;
import com.linger.module.toolhub.twofactor.dto.TwoFactorImportRequest;
import com.linger.module.toolhub.twofactor.dto.TwoFactorImportResponse;
import com.linger.module.toolhub.twofactor.model.TotpAlgorithm;
import com.linger.module.toolhub.twofactor.model.TwoFactorAccount;
import com.linger.module.totp.TotpNative;
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

    public List<TwoFactorAccountResponse> list(String userId, boolean includeSecret) {
        long now = System.currentTimeMillis() / 1000L;
        List<TwoFactorAccountResponse> result = new ArrayList<>();
        for (TwoFactorAccount account : load(userId)) {
            result.add(view(account, now, includeSecret));
        }
        return result;
    }

    public TwoFactorAccountResponse get(String userId, String accountId) {
        for (TwoFactorAccount account : load(userId)) {
            if (accountId.equals(account.getId())) return view(account, now(), true);
        }
        throw BusinessException.notFound("账号不存在");
    }

    public TwoFactorAccountResponse create(String userId, TwoFactorAccountRequest request) {
        TwoFactorAccount account = normalize(request, null);
        locked(userId, () -> {
            List<TwoFactorAccount> accounts = load(userId);
            accounts.add(0, account);
            save(userId, accounts);
            return null;
        });
        return view(account, now(), true);
    }

    public TwoFactorAccountResponse update(String userId, String accountId, TwoFactorAccountRequest request) {
        return locked(userId, () -> {
            List<TwoFactorAccount> accounts = load(userId);
            for (int index = 0; index < accounts.size(); index++) {
                TwoFactorAccount current = accounts.get(index);
                if (accountId.equals(current.getId())) {
                    TwoFactorAccount next = normalize(request, current);
                    next.setCreatedAt(current.getCreatedAt());
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
            List<TwoFactorAccount> accounts = load(userId);
            boolean removed = accounts.removeIf(item -> accountId.equals(item.getId()));
            if (!removed) throw BusinessException.notFound("账号不存在");
            save(userId, accounts);
            return null;
        });
    }

    public TwoFactorImportResponse importAccounts(String userId, TwoFactorImportRequest request) {
        List<TwoFactorAccount> parsed = new ArrayList<>();
        String text = request.getText();
        if (text != null && !text.trim().isEmpty()) parsed.addAll(parseImportText(text));
        if (request.getItems() != null) {
            for (TwoFactorAccountRequest item : request.getItems()) {
                if (item != null) parsed.add(normalize(item, null));
            }
        }
        if (parsed.isEmpty()) throw BusinessException.badRequest("没有可导入的账号");

        List<TwoFactorAccount> next = locked(userId, () -> {
            List<TwoFactorAccount> updated = "replace".equals(request.getMergeMode())
                    ? new ArrayList<>() : load(userId);
            Set<String> signatures = new LinkedHashSet<>();
            for (TwoFactorAccount item : updated) signatures.add(signature(item));
            for (TwoFactorAccount item : parsed) {
                if (signatures.add(signature(item))) updated.add(item);
            }
            save(userId, updated);
            return updated;
        });
        List<TwoFactorAccountResponse> views = new ArrayList<>();
        for (TwoFactorAccount item : parsed) views.add(view(item, now(), true));
        return new TwoFactorImportResponse(parsed.size(), next.size(), views);
    }

    public TwoFactorExportResponse<?> export(String userId, String format) {
        List<TwoFactorAccount> accounts = load(userId);
        if ("otpauth".equalsIgnoreCase(format)) {
            List<String> lines = new ArrayList<>();
            for (TwoFactorAccount account : accounts) lines.add(buildUri(account));
            return new TwoFactorExportResponse<>("otpauth", String.join("\n", lines));
        }
        if (!"json".equalsIgnoreCase(format)) throw BusinessException.badRequest("不支持的导出格式");
        List<TwoFactorAccountResponse> views = new ArrayList<>();
        for (TwoFactorAccount account : accounts) views.add(view(account, now(), true));
        return new TwoFactorExportResponse<>("json", views);
    }

    private List<TwoFactorAccount> parseImportText(String raw) {
        try {
            String text = raw.trim();
            if (text.startsWith("[")) {
                List<TwoFactorAccountRequest> items = JsonUtils.parseObject(text,
                        new TypeReference<List<TwoFactorAccountRequest>>() { });
                List<TwoFactorAccount> result = new ArrayList<>();
                for (TwoFactorAccountRequest item : items) result.add(normalize(item, null));
                return result;
            }
            List<TwoFactorAccount> result = new ArrayList<>();
            for (String line : text.split("\\r?\\n")) {
                if (line.trim().isEmpty()) continue;
                if (line.trim().startsWith("otpauth-migration://")) {
                    result.addAll(parseMigrationUri(line.trim()));
                } else {
                    TwoFactorAccountRequest payload = new TwoFactorAccountRequest();
                    payload.setOtpauthUri(line.trim());
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

    private TwoFactorAccount normalize(TwoFactorAccountRequest payload, TwoFactorAccount current) {
        TwoFactorAccountRequest source = merge(current, payload);
        String uri = payload.getOtpauthUri();
        if (uri != null && !uri.trim().isEmpty()) {
            if (uri.startsWith("otpauth-migration://")) {
                List<TwoFactorAccount> items = parseMigrationUri(uri);
                if (items.size() != 1) throw BusinessException.badRequest("Google 导出二维码包含多个账号，请使用导入功能");
                return withIdentity(items.get(0), current);
            } else {
                source = parseUri(uri);
            }
        }
        String secret = normalizeSecret(source.getSecret());
        String issuer = defaultString(source.getIssuer(), "Tool Hub");
        String accountName = defaultString(source.getAccountName(), "");
        String label = defaultString(source.getLabel(), accountName.isEmpty() ? issuer : issuer + " (" + accountName + ")");
        TotpAlgorithm algorithm = source.getAlgorithm() == null ? TotpAlgorithm.SHA1 : source.getAlgorithm();
        int digits = defaultInteger(source.getDigits(), 6);
        if (digits != 6 && digits != 8) throw BusinessException.badRequest("digits 仅支持 6 或 8");
        int period = defaultInteger(source.getPeriod(), 30);
        if (period <= 0) throw BusinessException.badRequest("period 必须大于 0");
        String now = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return new TwoFactorAccount(
                current == null ? newAccountId() : current.getId(),
                label,
                issuer,
                accountName,
                secret,
                digits,
                period,
                algorithm,
                current == null ? now : current.getCreatedAt(),
                now);
    }

    private TwoFactorAccountRequest merge(TwoFactorAccount current, TwoFactorAccountRequest request) {
        if (current == null) return request;
        TwoFactorAccountRequest merged = new TwoFactorAccountRequest();
        merged.setLabel(request.getLabel() == null ? current.getLabel() : request.getLabel());
        merged.setIssuer(request.getIssuer() == null ? current.getIssuer() : request.getIssuer());
        merged.setAccountName(request.getAccountName() == null ? current.getAccountName() : request.getAccountName());
        merged.setSecret(request.getSecret() == null ? current.getSecret() : request.getSecret());
        merged.setDigits(request.getDigits() == null ? current.getDigits() : request.getDigits());
        merged.setPeriod(request.getPeriod() == null ? current.getPeriod() : request.getPeriod());
        merged.setAlgorithm(request.getAlgorithm() == null ? current.getAlgorithm() : request.getAlgorithm());
        merged.setOtpauthUri(request.getOtpauthUri());
        return merged;
    }

    private TwoFactorAccount withIdentity(TwoFactorAccount account, TwoFactorAccount current) {
        if (current == null) return account;
        account.setId(current.getId());
        account.setCreatedAt(current.getCreatedAt());
        return account;
    }

    private TwoFactorAccountRequest parseUri(String value) {
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
            TwoFactorAccountRequest result = new TwoFactorAccountRequest();
            result.setLabel(label);
            result.setIssuer(defaultString(query.get("issuer"), issuerFromLabel));
            result.setAccountName(accountName);
            result.setSecret(query.get("secret"));
            result.setDigits(parseInteger(query.get("digits")));
            result.setPeriod(parseInteger(query.get("period")));
            if (query.get("algorithm") != null && !query.get("algorithm").trim().isEmpty()) {
                result.setAlgorithm(TotpAlgorithm.fromValue(query.get("algorithm")));
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw BusinessException.badRequest("otpauth 链接格式不正确");
        }
    }

    private List<TwoFactorAccount> parseMigrationUri(String value) {
        try {
            URI uri = URI.create(value);
            if (!"otpauth-migration".equalsIgnoreCase(uri.getScheme()) || !"offline".equalsIgnoreCase(uri.getHost())) {
                throw BusinessException.badRequest("Google 导出二维码格式不正确");
            }
            String data = query(uri.getRawQuery()).get("data");
            if (data == null) throw BusinessException.badRequest("Google 导出二维码缺少 data 参数");
            byte[] bytes = Base64.getUrlDecoder().decode(padBase64(data));
            List<ProtoField> migration = proto(bytes);
            List<TwoFactorAccount> result = new ArrayList<>();
            for (ProtoField field : migration) {
                if (field.number == 1 && field.bytes != null) {
                    TwoFactorAccountRequest item = migrationAccount(proto(field.bytes));
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

    private TwoFactorAccountRequest migrationAccount(List<ProtoField> fields) {
        TwoFactorAccountRequest result = new TwoFactorAccountRequest();
        int type = 2;
        for (ProtoField field : fields) {
            if (field.number == 1 && field.bytes != null) result.setSecret(base32(field.bytes));
            if (field.number == 2 && field.bytes != null) result.setAccountName(new String(field.bytes, StandardCharsets.UTF_8));
            if (field.number == 3 && field.bytes != null) result.setIssuer(new String(field.bytes, StandardCharsets.UTF_8));
            if (field.number == 4) result.setAlgorithm(field.value == 2
                    ? TotpAlgorithm.SHA256 : field.value == 3 ? TotpAlgorithm.SHA512 : TotpAlgorithm.SHA1);
            if (field.number == 5) result.setDigits(field.value == 2 ? 8 : 6);
            if (field.number == 6) type = (int) field.value;
        }
        if (type != 2) return null;
        result.setPeriod(30);
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

    private TwoFactorAccountResponse view(TwoFactorAccount account, long now, boolean includeSecret) {
        int period = defaultInteger(account.getPeriod(), 30);
        int digits = defaultInteger(account.getDigits(), 6);
        String secret = account.getSecret();
        String code;
        try {
            code = TotpNative.generateTotpAtTime(secret, now / period, account.getAlgorithm().getValue(), digits);
        } catch (Exception exception) {
            throw new IllegalStateException("TOTP 生成失败", exception);
        }
        return TwoFactorAccountResponse.builder()
                .id(account.getId())
                .label(account.getLabel())
                .issuer(account.getIssuer())
                .accountName(account.getAccountName())
                .digits(digits)
                .period(period)
                .algorithm(account.getAlgorithm())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .code(code)
                .secondsRemaining(period - now % period)
                .secretMasked(mask(secret))
                .secret(includeSecret ? secret : null)
                .otpauthUri(includeSecret ? buildUri(account) : null)
                .build();
    }

    private String buildUri(TwoFactorAccount account) {
        String accountName = account.getAccountName();
        String label = accountName == null || accountName.isEmpty()
                ? account.getLabel() : account.getIssuer() + ":" + accountName;
        return "otpauth://totp/" + encode(label) + "?secret=" + account.getSecret() +
                "&issuer=" + encode(account.getIssuer()) + "&algorithm=" + account.getAlgorithm().getValue() +
                "&digits=" + account.getDigits() + "&period=" + account.getPeriod();
    }

    private List<TwoFactorAccount> load(String userId) {
        RBucket<String> bucket = redissonClient.getBucket(key(userId), StringCodec.INSTANCE);
        String value = bucket.get();
        if (value == null || value.isEmpty()) return new ArrayList<>();
        try {
            boolean legacyPlainText = !value.startsWith("v1:");
            String json = legacyPlainText ? value : crypto.decrypt(value);
            List<TwoFactorAccount> accounts = JsonUtils.parseObject(json,
                    new TypeReference<List<TwoFactorAccount>>() { });
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

    private void save(String userId, List<TwoFactorAccount> accounts) {
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

    private String signature(TwoFactorAccount item) {
        return item.getIssuer() + "|" + item.getAccountName() + "|" + item.getSecret() +
                "|" + item.getDigits() + "|" + item.getPeriod() + "|" + item.getAlgorithm().getValue();
    }

    private String mask(String secret) {
        if (secret.length() <= 8) return secret;
        return secret.substring(0, 4) + String.join("", Collections.nCopies(secret.length() - 8, "*")) + secret.substring(secret.length() - 4);
    }

    private int defaultInteger(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw BusinessException.badRequest("数字参数格式不正确");
        }
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String newAccountId() {
        return "totp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
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
