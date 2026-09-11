package com.linger.module.annotation;


import com.linger.module.util.LogFilterUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RedisLockAspect {

    private final RedissonClient redissonClient;

    private static final String DEFAULT_PROMPT = "请不要重复操作！";
    private static final String LOCK_PREFIX = "lock:";

    @Around("@annotation(redisLock)")
    public Object around(ProceedingJoinPoint joinPoint, RedisLock redisLock) throws Throwable {

        List<String> keys = buildKeys(joinPoint, redisLock);
        if (keys.isEmpty()) {
            return joinPoint.proceed();
        }

        String prompt = resolvePrompt(joinPoint, redisLock);

        // interval 限流检查
        checkInterval(keys.get(0), redisLock, prompt);

        RLock multiLock = buildMultiLock(keys);

        boolean locked = false;
        try {
            locked = multiLock.tryLock(2, -1, TimeUnit.SECONDS);
            if (!locked) {
                throw new Exception(prompt);
            }
            return joinPoint.proceed();
        } finally {
            safeUnlock(multiLock, keys);
        }
    }

    /** 解析 SpEL 并构建锁 key */
    private List<String> buildKeys(ProceedingJoinPoint joinPoint, RedisLock redisLock) {
        String baseKey = getDefaultKey(joinPoint);

        if (redisLock.key().isEmpty()) {
            return Collections.singletonList(baseKey);
        }

        try {
            Object value = parseSpEL(joinPoint, redisLock.key());
            if (value == null) {
                return Collections.emptyList();
            }

            if (value instanceof Collection<?>) {
                return ((Collection<?>) value)
                        .stream()
                        .map(v -> baseKey + ":" + v)
                        .collect(Collectors.toList());
            }

            if (value.getClass().isArray()) {
                List<String> keys = new ArrayList<>();
                int length = Array.getLength(value);
                for (int i = 0; i < length; i++) {
                    keys.add(baseKey + ":" + Array.get(value, i));
                }
                return keys;
            }

            return Collections.singletonList(baseKey + ":" + value);

        } catch (Exception e) {
            log.warn("解析 SpEL key 失败，将使用默认 key，expr={}", redisLock.key(), e);
            return Collections.singletonList(baseKey);
        }
    }

    /** 多锁 */
    private RLock buildMultiLock(List<String> keys) {
        List<RLock> lockList = keys.stream()
                .map(redissonClient::getLock)
                .collect(Collectors.toList());
        return new RedissonMultiLock(lockList.toArray(new RLock[0]));
    }

    /** safe unlock */
    private void safeUnlock(RLock multiLock, List<String> keys) {
        try {
            multiLock.unlock();
        } catch (IllegalMonitorStateException e) {
            log.warn("锁未持有或已自动释放: {}", keys);
        } catch (Throwable t) {
            log.error("释放锁异常 key={}", keys, t);
        }
    }

    /** 限制点击频率 */
    private void checkInterval(String key, RedisLock redisLock, String prompt) throws Exception {
        if (redisLock.interval() <= 0) {
            return;
        }

        String intervalKey = key + ":interval";
        boolean ok = redissonClient.getBucket(intervalKey)
                .trySet("1", redisLock.interval(), TimeUnit.SECONDS);

        if (!ok) {
            throw new Exception(prompt);
        }
    }

    /** 提示语解析 */
    private String resolvePrompt(ProceedingJoinPoint joinPoint, RedisLock redisLock) {
        if (redisLock.prompt().isEmpty()) {
            return DEFAULT_PROMPT;
        }

        try {
            Object result = parseSpEL(joinPoint, redisLock.prompt());
            // 处理解析结果为null的情况
            if (result == null) {
                log.warn("SpEL解析提示语返回null，使用默认提示语: {}", redisLock.prompt());
                return DEFAULT_PROMPT;
            }
            return result.toString();
        } catch (Exception e) {
            log.warn("解析提示语SpEL失败，使用默认提示语: {}", redisLock.prompt(), e);
            return DEFAULT_PROMPT;
        }
    }

    /** SpEL 工具 */
    private Object parseSpEL(ProceedingJoinPoint joinPoint, String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }

        try {
            ExpressionParser parser = new SpelExpressionParser();
            Expression exp = parser.parseExpression(expression);

            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = sig.getParameterNames();
            Object[] args = joinPoint.getArgs();

            StandardEvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null && args != null) {
                for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            return exp.getValue(context);
        } catch (Exception e) {
            log.warn("SpEL解析异常: {}", expression, e);
            throw e; // 重新抛出异常，让调用方处理
        }
    }

    /**
     * 默认key格式: lock:类名:方法名:参数值...
     */
    private String getDefaultKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        StringBuilder sb = new StringBuilder();
        sb.append(className).append(":").append(methodName);

        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (LogFilterUtils.isNonLoggable(arg)) continue;
            sb.append(":").append(arg);
        }

        return LOCK_PREFIX + sb;
    }
}
