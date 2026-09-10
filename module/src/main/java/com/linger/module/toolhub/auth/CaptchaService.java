package com.linger.module.toolhub.auth;

import com.linger.module.exception.BusinessException;
import com.linger.module.toolhub.auth.dto.CaptchaResponse;
import com.linger.module.toolhub.config.ToolHubProperties;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final RedissonClient redissonClient;
    private final ToolHubProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CaptchaResponse create() {
        String code = randomCode();
        String uuid = UUID.randomUUID().toString();
        RBucket<String> bucket = redissonClient.getBucket(key(uuid), StringCodec.INSTANCE);
        bucket.set(code, properties.getCaptchaTtlSeconds(), TimeUnit.SECONDS);

        return new CaptchaResponse(true, uuid, render(code));
    }

    public void validate(String uuid, String code) {
        if (uuid == null || code == null) {
            throw BusinessException.badRequest("验证码错误");
        }
        RBucket<String> bucket = redissonClient.getBucket(key(uuid.trim()), StringCodec.INSTANCE);
        String expected = bucket.getAndDelete();
        if (expected == null || !expected.equalsIgnoreCase(code.trim())) {
            throw BusinessException.badRequest("验证码错误或已过期");
        }
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    private String render(String code) {
        try {
            BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 120, 40);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            for (int index = 0; index < 4; index++) {
                graphics.setColor(new Color(30 + random.nextInt(150), 30 + random.nextInt(150), 30 + random.nextInt(150)));
                graphics.drawString(String.valueOf(code.charAt(index)), 13 + index * 25, 29 + random.nextInt(4));
            }
            for (int index = 0; index < 4; index++) {
                graphics.setColor(new Color(random.nextInt(180), random.nextInt(180), random.nextInt(180)));
                graphics.drawLine(random.nextInt(120), random.nextInt(40), random.nextInt(120), random.nextInt(40));
            }
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("验证码生成失败", exception);
        }
    }

    private String key(String uuid) {
        return "toolhub:captcha:" + uuid;
    }
}
