package com.linger.module.util;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @description RedisUtil
 * @date 2025/12/11 18:04:55
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedissonClient redissonClient;

    /**
     * 生成带有前缀 + 日期 + 自增序列 的订单号（每天自动从 1 开始）
     */
    public String getOrderSerialNumber(String keyPrefix, String prefix, String dateFormat, long digit) {

        // 当天日期
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern(dateFormat));

        // 每天形成独立 key，例如：ORDER_SERIAL:20251211
        String redisKey = keyPrefix + ":" + dateStr;

        RAtomicLong atomicLong = redissonClient.getAtomicLong(redisKey);

        // 设置过期时间，防止 Redis 无限制堆积
        atomicLong.expire(2, TimeUnit.DAYS);

        // 递增序列（从 1 开始）
        long increment = atomicLong.incrementAndGet();

        // 格式化指定位数（如 6 → 000001）
        String serial = String.format("%0" + digit + "d", increment);

        return prefix + dateStr + serial;
    }

}
