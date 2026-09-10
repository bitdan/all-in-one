package com.linger.module.redisson;


import com.linger.module.redisson.service.RedissonService;
import com.linger.module.common.http.HttpResponseData;
import com.linger.module.util.HttpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @version 1.0
 * @description ConcurrentGrabTest
 * @date 2025/7/21 17:41:52
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RedissonConcurrentTest {

    @Autowired
    private RedissonService redissonService;

    @Autowired
    private HttpUtil httpUtil;

    private final String url = "http://localhost:9999/redis/grabTask";

    @Test
    public void testConcurrentGrabWithCompletableFuture() {
        int threadCount = 30;

        List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    String userId = "user" + i;

                    // 使用 UriComponentsBuilder 构建完整 URL
                    String fullUrl = UriComponentsBuilder.fromHttpUrl(url)
                            .queryParam("userId", userId)
                            .build()
                            .toUriString();

                    try {
                        HttpResponseData response = httpUtil.get(fullUrl, null);
                        int responseCode = response.getStatusCode();
                        String body = response.bodyAsString();

                        System.out.println("用户 " + userId + " => 响应码: " + responseCode + "，内容: " + body);
                    } catch (Exception e) {
                        System.err.println("用户 " + userId + " 请求异常：" + e.getMessage());
                    }
                })).collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private static final int USER_COUNT = 10_000;
    private static final int THREAD_COUNT = 100;
    private static final int REQUESTS_PER_THREAD = 100;
    private static final String TEST_PRODUCT = "P20240722-HotSell";

    // 生成测试用户ID
    private List<Long> testUserIds;

    @BeforeEach
    void setUp() {
        // 生成测试用户
        testUserIds = LongStream.range(1, USER_COUNT + 1)
                .boxed()
                .collect(Collectors.toList());

        // 清理测试数据
        for (Long userId : testUserIds) {
            redissonService.resetUserPurchase(userId, TEST_PRODUCT);
        }
        redissonService.resetGlobalSoldCount(TEST_PRODUCT);
    }

    /**
     * 测试场景1：所有用户同时抢购限1个商品（每人限购1件）
     */
    @Test
    void testConcurrentSingleItemLimit() throws InterruptedException {
        final int PER_USER_LIMIT = 1;
        final int GLOBAL_LIMIT = 10000; // 全局库存限制

        AtomicInteger successfulPurchases = new AtomicInteger();
        AtomicInteger failedPurchases = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT * REQUESTS_PER_THREAD);

        Random random = new Random();

        for (int i = 0; i < THREAD_COUNT * REQUESTS_PER_THREAD; i++) {
            executor.execute(() -> {
                try {
                    // 随机选择用户
                    Long userId = testUserIds.get(random.nextInt(USER_COUNT));

                    if (redissonService.purchaseItem(userId, TEST_PRODUCT,
                            PER_USER_LIMIT, GLOBAL_LIMIT, 3600)) {
                        successfulPurchases.incrementAndGet();
                    } else {
                        failedPurchases.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 获取全局库存
        long globalSold = redissonService.getGlobalSoldCount(TEST_PRODUCT);

        System.out.println("================ 测试结果 =================");
        System.out.println("总请求数: " + (THREAD_COUNT * REQUESTS_PER_THREAD));
        System.out.println("成功购买数: " + successfulPurchases.get());
        System.out.println("失败购买数: " + failedPurchases.get());
        System.out.println("Redis全局库存: " + globalSold);
        System.out.println("==========================================");

        // 验证每个用户的购买数量不超过1
        testUserIds.stream()
                .map(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .filter(count -> count > PER_USER_LIMIT)
                .findAny()
                .ifPresent(count -> fail("存在用户购买数超过限制: " + count));

        // 验证全局库存不超过限制
        assertTrue(globalSold <= GLOBAL_LIMIT, "全局库存超过限制: " + globalSold);
    }

    /**
     * 测试场景2：单个商品限购10000件，每人限购5件
     */
    @Test
    void testGlobalItemLimit() throws InterruptedException {
        final int GLOBAL_LIMIT = 10000;
        final int PER_USER_LIMIT = 5;

        AtomicInteger successfulPurchases = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT * REQUESTS_PER_THREAD);

        // 使用线程安全的计数器
        for (int i = 0; i < THREAD_COUNT * REQUESTS_PER_THREAD; i++) {
            executor.execute(() -> {
                try {
                    Long userId = testUserIds.get(new Random().nextInt(USER_COUNT));
                    if (redissonService.purchaseItem(userId, TEST_PRODUCT,
                            PER_USER_LIMIT, GLOBAL_LIMIT, 3600)) {
                        successfulPurchases.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 统计实际售出数量
        long actualSold = testUserIds.stream()
                .mapToLong(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .sum();

        // 获取全局库存
        long globalSold = redissonService.getGlobalSoldCount(TEST_PRODUCT);

        System.out.println("================ 详细测试结果 =================");
        System.out.println("预期最大销售数: " + GLOBAL_LIMIT);
        System.out.println("实际销售总数: " + actualSold);
        System.out.println("Redis全局库存: " + globalSold);
        System.out.println("未达标差值: " + (GLOBAL_LIMIT - actualSold));

        // 添加用户购买分布统计
        Map<Long, Long> purchaseDistribution = testUserIds.stream()
                .map(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .collect(Collectors.groupingBy(count -> count, Collectors.counting()));

        System.out.println("\n用户购买数量分布:");
        purchaseDistribution.forEach((count, numUsers) ->
                System.out.printf("%d件: %d位用户 (%.1f%%)\n",
                        count, numUsers, numUsers * 100.0 / USER_COUNT)
        );

        // 统计超额购买用户
        long usersOverLimit = purchaseDistribution.entrySet().stream()
                .filter(entry -> entry.getKey() > PER_USER_LIMIT)
                .mapToLong(Map.Entry::getValue)
                .sum();

        System.out.println("\n超额购买用户数: " + usersOverLimit);
        System.out.println("==========================================");

        // 关键断言
        assertTrue(actualSold <= GLOBAL_LIMIT, "销售总数超过限制: " + actualSold);
        assertEquals(0, usersOverLimit, "存在超额购买用户");
        assertEquals(actualSold, globalSold, "用户购买总数与全局库存不一致");
    }

    /**
     * 边界测试：库存为0时拒绝所有购买请求
     */
    @Test
    void testZeroInventory() throws InterruptedException {
        final int GLOBAL_LIMIT = 0; // 库存为0
        final int PER_USER_LIMIT = 5;

        AtomicInteger successfulPurchases = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT * REQUESTS_PER_THREAD);

        for (int i = 0; i < THREAD_COUNT * REQUESTS_PER_THREAD; i++) {
            executor.execute(() -> {
                try {
                    Long userId = testUserIds.get(new Random().nextInt(USER_COUNT));
                    if (redissonService.purchaseItem(userId, TEST_PRODUCT,
                            PER_USER_LIMIT, GLOBAL_LIMIT, 3600)) {
                        successfulPurchases.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long globalSold = redissonService.getGlobalSoldCount(TEST_PRODUCT);

        System.out.println("================ 零库存测试结果 =================");
        System.out.println("成功购买数: " + successfulPurchases.get());
        System.out.println("Redis全局库存: " + globalSold);
        System.out.println("==========================================");

        assertEquals(0, successfulPurchases.get(), "零库存下不应有成功购买");
        assertEquals(0, globalSold, "全局库存应为0");
    }

    /**
     * 精确边界测试：库存刚好满足所有请求
     */
    @Test
    void testPreciseGlobalLimitAtBoundary() throws InterruptedException {
        final int GLOBAL_LIMIT = 1000;
        final int PER_USER_LIMIT = 10;
        final int USER_COUNT = 10000;

        // 重置全局库存
        redissonService.resetGlobalSoldCount(TEST_PRODUCT);

        AtomicInteger successfulPurchases = new AtomicInteger();
        ExecutorService executor = Executors.newWorkStealingPool(THREAD_COUNT);

        // 精确请求数量 = 库存上限 * 2 (确保超出)
        int totalRequests = GLOBAL_LIMIT * 2;
        CountDownLatch latch = new CountDownLatch(totalRequests);

        // 使用固定用户池，确保用户重复尝试购买
        List<Long> testUserPool = testUserIds.subList(0, USER_COUNT);
        Collections.shuffle(testUserPool);

        for (int i = 0; i < totalRequests; i++) {
            // 循环使用用户ID
            Long userId = testUserPool.get(i % testUserPool.size());
            executor.submit(() -> {
                try {
                    if (redissonService.purchaseItem(
                            userId,
                            TEST_PRODUCT,
                            PER_USER_LIMIT,
                            GLOBAL_LIMIT,
                            3600)) {
                        successfulPurchases.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证全局库存
        long redisGlobalCount = redissonService.getGlobalSoldCount(TEST_PRODUCT);
        long userSumCount = testUserIds.stream()
                .mapToLong(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .sum();

        System.out.println("================ 边界测试结果 =================");
        System.out.println("预期最大销售数: " + GLOBAL_LIMIT);
        System.out.println("实际售出总数: " + redisGlobalCount);
        System.out.println("用户购买总数: " + userSumCount);
        System.out.println("实际成功请求数: " + successfulPurchases.get());
        System.out.println("==========================================");

        // 关键断言
        assertTrue(redisGlobalCount <= GLOBAL_LIMIT,
                "Redis全局库存超过限制: " + redisGlobalCount);

        assertTrue(userSumCount <= GLOBAL_LIMIT,
                "用户购买总数超过限制: " + userSumCount);

        assertEquals(redisGlobalCount, userSumCount,
                "全局库存与用户购买总数不一致: " + redisGlobalCount + " vs " + userSumCount);

        // 验证无用户超限
        testUserIds.stream()
                .map(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .filter(count -> count > PER_USER_LIMIT)
                .findAny()
                .ifPresent(count -> fail("用户超限购买: " + count));
    }

    /**
     * 性能基准测试
     */
    @Test
    void benchmarkPerformance() throws InterruptedException {
        final int USER_SAMPLE_SIZE = 100;
        final int REQUESTS = 10_000;
        final int GLOBAL_LIMIT = 20000; // 设置足够大的库存

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(REQUESTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < REQUESTS; i++) {
            Long userId = testUserIds.get(i % USER_SAMPLE_SIZE);
            executor.execute(() -> {
                try {
                    // 使用带全局库存的购买方法
                    redissonService.purchaseItem(userId, TEST_PRODUCT, 5, GLOBAL_LIMIT, 3600);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long duration = System.currentTimeMillis() - startTime;
        executor.shutdown();

        System.out.println("================ 性能测试 =================");
        System.out.println("总请求数: " + REQUESTS);
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("QPS: " + (REQUESTS / (duration / 1000.0)) + "/秒");
        System.out.println("=========================================");
    }

    /**
     * 高并发一致性测试
     */
    @Test
    void testConcurrentConsistency() throws InterruptedException {
        final int GLOBAL_LIMIT = 5000;
        final int PER_USER_LIMIT = 3;
        final int REQUESTS = 100000;

        // 使用动态用户池大小
        final int USER_POOL_SIZE = testUserIds.size();

        // 重置所有状态
        setUp();

        AtomicInteger successfulPurchases = new AtomicInteger();
        ExecutorService executor = Executors.newWorkStealingPool(200);
        CountDownLatch latch = new CountDownLatch(REQUESTS);

        Random random = new Random();

        for (int i = 0; i < REQUESTS; i++) {
            executor.execute(() -> {
                try {
                    // 使用正确的用户池大小
                    Long userId = testUserIds.get(random.nextInt(USER_POOL_SIZE));
                    if (redissonService.purchaseItem(userId, TEST_PRODUCT,
                            PER_USER_LIMIT, GLOBAL_LIMIT, 3600)) {
                        successfulPurchases.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("Error in purchase: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // 验证最终一致性
        long redisGlobal = redissonService.getGlobalSoldCount(TEST_PRODUCT);
        long userSum = testUserIds.stream()
                .mapToLong(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .sum();
        List<Long> overPurchases = testUserIds.stream()
                .map(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .filter(count -> count > PER_USER_LIMIT)
                .collect(Collectors.toList());

        // 添加用户购买分布统计
        Map<Long, Long> purchaseDistribution = testUserIds.stream()
                .map(userId -> redissonService.getUserPurchaseCount(userId, TEST_PRODUCT))
                .collect(Collectors.groupingBy(count -> count, Collectors.counting()));

        System.out.println("============= 最终一致性验证 =============");
        System.out.println("Redis全局库存: " + redisGlobal);
        System.out.println("用户购买合计: " + userSum);
        System.out.println("超购用户数: " + overPurchases.size());
        System.out.println("\n用户购买数量分布:");
        purchaseDistribution.forEach((count, numUsers) ->
                System.out.printf("%d件: %d位用户 (%.1f%%)\n",
                        count, numUsers, numUsers * 100.0 / USER_COUNT)
        );
        System.out.println("========================================");

        // 关键断言
        assertEquals(redisGlobal, userSum, "全局库存与用户购买不一致");
        assertTrue(overPurchases.isEmpty(), "存在超购用户: " + overPurchases.size());
        assertTrue(userSum <= GLOBAL_LIMIT, "总购买量超过限制");
    }
}

