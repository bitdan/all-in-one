package com.linger.module.groupbuy.transaction.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.linger.module.util.JsonUtils;
import com.linger.LingerApplication;
import com.linger.module.groupbuy.transaction.dto.CreateActivityRequest;
import com.linger.module.groupbuy.transaction.dto.CreateGroupRequest;
import com.linger.module.groupbuy.transaction.dto.PaymentCallbackRequest;
import com.linger.module.groupbuy.transaction.dto.PlaceGroupOrderRequest;
import com.linger.module.groupbuy.group.entity.GroupBuyGroupEntity;
import com.linger.module.groupbuy.inventory.entity.GroupBuyInventoryLedgerEntity;
import com.linger.module.groupbuy.inventory.entity.GroupBuyInventoryReservationEntity;
import com.linger.module.groupbuy.inventory.entity.GroupBuyInventoryStockEntity;
import com.linger.module.groupbuy.group.entity.GroupBuyMemberEntity;
import com.linger.module.groupbuy.order.entity.GroupBuyOrderEntity;
import com.linger.module.groupbuy.group.mapper.GroupBuyGroupMapper;
import com.linger.module.groupbuy.inventory.mapper.GroupBuyInventoryLedgerMapper;
import com.linger.module.groupbuy.inventory.mapper.GroupBuyInventoryReservationMapper;
import com.linger.module.groupbuy.inventory.mapper.GroupBuyInventoryStockMapper;
import com.linger.module.groupbuy.group.mapper.GroupBuyMemberMapper;
import com.linger.module.groupbuy.order.mapper.GroupBuyOrderMapper;
import com.linger.module.groupbuy.group.model.GroupInstanceStatus;
import com.linger.module.groupbuy.order.model.GroupOrderStatus;
import com.linger.module.groupbuy.inventory.model.InventoryReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拼团交易接口真实并发集成测试。
 *
 * <p>测试启动随机端口 Undertow，通过真实 HTTP 请求访问 Controller，并使用 local profile 中配置的
 * PostgreSQL 和 Redis。local Profile 会在启动测试上下文时自动完成迁移。每次运行使用唯一活动和 SKU，测试数据
 * 会保留在数据库与 Redis 中，方便执行后人工对账。</p>
 */
@Slf4j
@ActiveProfiles("local")
@SpringBootTest(
        classes = LingerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "groupbuy.transaction.outbox-poll-ms=100",
                "spring.datasource.hikari.minimum-idle=20"
        }
)
class GroupBuyTransactionControllerConcurrencyTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("19.90");

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private GroupBuyGroupMapper groupMapper;
    @Autowired
    private GroupBuyOrderMapper orderMapper;
    @Autowired
    private GroupBuyMemberMapper memberMapper;
    @Autowired
    private GroupBuyInventoryLedgerMapper ledgerMapper;
    @Autowired
    private GroupBuyInventoryStockMapper inventoryStockMapper;
    @Autowired
    private GroupBuyInventoryReservationMapper inventoryReservationMapper;
    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void configureHttpTimeout() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(
                positiveSystemProperty("groupbuy.concurrent.http-read-timeout-ms", 60_000));
        // TestRestTemplate 默认读取超时不足以覆盖远程数据库热点行排队，压测中显式区分客户端超时和服务端失败。
        restTemplate.getRestTemplate().setRequestFactory(requestFactory);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void shouldKeepPostgresAndRedisConsistentUnderRealHttpConcurrency() throws Exception {
        int capacity = positiveSystemProperty("groupbuy.concurrent.capacity", 20);
        int requestCount = positiveSystemProperty("groupbuy.concurrent.requests", 50);
        int threadCount = Math.min(requestCount,
                positiveSystemProperty("groupbuy.concurrent.threads", 20));
        assertTrue(requestCount > capacity, "并发请求数必须大于库存，才能验证超卖保护");

        String runId = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String skuId = "CONCURRENT-" + runId;
        TestContext context = createAndPublishActivity(runId, skuId, capacity);

        log.info("开始真实并发下单：runId={}, activityId={}, groupId={}, skuId={}, capacity={}, requests={}, threads={}",
                runId, context.getActivityId(), context.getGroupId(), skuId,
                capacity, requestCount, threadCount);

        BatchResult orderBatch = executeConcurrently("并发下单", requestCount, threadCount, index -> {
            PlaceGroupOrderRequest request = new PlaceGroupOrderRequest();
            request.setRequestId("REQ-" + runId + "-" + index);
            request.setUserId(1_000_000L + index);
            request.setActivityId(context.getActivityId());
            request.setGroupId(context.getGroupId());
            request.setQuantity(1);
            return post("/api/v1/groupbuy/orders", request);
        });

        List<String> acceptedOrderIds = orderBatch.getResults().stream()
                .filter(result -> "ACCEPTED".equals(result.getCode()))
                .map(HttpCallResult::getOrderId)
                .collect(Collectors.toList());
        assertBatchCompleted(orderBatch, requestCount);
        assertEquals(capacity, acceptedOrderIds.size(), "成功订单数应等于库存和团容量");
        assertEquals(requestCount - capacity, orderBatch.count("OUT_OF_STOCK"),
                "超过库存的请求应全部被 Redis Lua 拒绝");

        verifyReservationState(context, skuId, capacity, requestCount, acceptedOrderIds);

        BatchResult paymentBatch = executeConcurrently("并发支付回调", acceptedOrderIds.size(), threadCount, index -> {
            PaymentCallbackRequest request = new PaymentCallbackRequest();
            request.setOrderId(acceptedOrderIds.get(index));
            request.setPaymentNo("PAY-" + runId + "-" + index);
            request.setPaidAmount(UNIT_PRICE);
            request.setPaidAt(OffsetDateTime.now(ZoneOffset.UTC));
            return post("/api/v1/groupbuy/payments/callback", request);
        });

        assertBatchCompleted(paymentBatch, capacity);
        assertEquals(capacity, paymentBatch.dataCount("PAYMENT_ACCEPTED"),
                "所有已预占订单的支付回调都应被接受");

        waitForGroupSuccess(context.getGroupId(), capacity);
        verifyPaidState(context, skuId, capacity, requestCount);

        log.info("并发链路验证完成：runId={}, activityId={}, groupId={}, acceptedOrders={}",
                runId, context.getActivityId(), context.getGroupId(), acceptedOrderIds.size());
    }

    private TestContext createAndPublishActivity(String runId, String skuId, int capacity) throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CreateActivityRequest activityRequest = new CreateActivityRequest();
        activityRequest.setName("真实并发接口测试-" + runId);
        activityRequest.setSkuId(skuId);
        activityRequest.setUnitPrice(UNIT_PRICE);
        activityRequest.setTotalStock(capacity);
        activityRequest.setPerUserLimit(1);
        activityRequest.setTargetCount(capacity);
        activityRequest.setPayTimeoutSeconds(600L);
        activityRequest.setGroupTimeoutSeconds(1800L);
        activityRequest.setStartsAt(now.minusMinutes(1));
        activityRequest.setEndsAt(now.plusHours(1));

        HttpCallResult created = post("/api/v1/groupbuy/activities", activityRequest);
        assertEquals("SUCCESS", created.getCode(), "创建活动接口失败：" + created.getBody());
        long activityId = created.getJson().path("data").path("id").asLong();

        HttpCallResult published = post("/api/v1/groupbuy/activities/" + activityId + "/publish", null);
        assertEquals("SUCCESS", published.getCode(), "发布活动接口失败：" + published.getBody());

        CreateGroupRequest groupRequest = new CreateGroupRequest();
        groupRequest.setCreatorUserId(900_000L);
        HttpCallResult groupCreated = post(
                "/api/v1/groupbuy/activities/" + activityId + "/groups", groupRequest);
        assertEquals("SUCCESS", groupCreated.getCode(), "创建拼团接口失败：" + groupCreated.getBody());
        long groupId = groupCreated.getJson().path("data").path("id").asLong();

        log.info("测试数据初始化完成：activity={}, group={}",
                created.getJson().path("data"), groupCreated.getJson().path("data"));
        return new TestContext(activityId, groupId);
    }

    private BatchResult executeConcurrently(String scene,
                                            int taskCount,
                                            int threadCount,
                                            IntFunction<HttpCallResult> action) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);
        List<HttpCallResult> results = Collections.synchronizedList(new ArrayList<>());
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        long batchStartedAt = System.nanoTime();

        try {
            for (int i = 0; i < taskCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        results.add(action.apply(index));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        errors.add("task=" + index + ", interrupted=" + e.getMessage());
                    } catch (Exception e) {
                        errors.add("task=" + index + ", error=" + e.getClass().getSimpleName()
                                + ": " + e.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(15, TimeUnit.SECONDS), scene + "工作线程未能按时就绪");
            start.countDown();
            assertTrue(done.await(90, TimeUnit.SECONDS), scene + "未在90秒内完成");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), scene + "线程池未正常结束");
        }

        BatchResult batch = new BatchResult(results, new ArrayList<>(errors),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStartedAt));
        log.info("{}汇总：total={}, elapsedMs={}, throughputPerSecond={}, p95Ms={}, codes={}, data={}",
                scene, results.size(), batch.getElapsedMs(), batch.throughputPerSecond(),
                batch.p95LatencyMs(), batch.codeCounts(), batch.dataCounts());
        if (!errors.isEmpty()) {
            log.error("{}客户端异常：{}", scene, errors);
        }
        return batch;
    }

    private HttpCallResult post(String path, Object request) {
        long startedAt = System.nanoTime();
        ResponseEntity<String> response = restTemplate.postForEntity(path, request, String.class);
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return parseResponse(path, response, latencyMs);
    }

    private HttpCallResult get(String path) {
        long startedAt = System.nanoTime();
        ResponseEntity<String> response = restTemplate.getForEntity(path, String.class);
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return parseResponse(path, response, latencyMs);
    }

    private HttpCallResult parseResponse(String path, ResponseEntity<String> response, long latencyMs) {
        try {
            JsonNode json = JsonUtils.objectMapper.readTree(response.getBody());
            JsonNode data = json.path("data");
            String orderId = data.isObject() ? textOrNull(data.path("orderId")) : null;
            String dataText = data.isTextual() ? data.asText() : null;
            return new HttpCallResult(response.getStatusCodeValue(), json.path("code").asText(),
                    orderId, dataText, latencyMs, response.getBody(), json);
        } catch (Exception e) {
            throw new IllegalStateException("接口响应不是合法JSON, path=" + path
                    + ", status=" + response.getStatusCodeValue() + ", body=" + response.getBody(), e);
        }
    }

    private void verifyReservationState(TestContext context,
                                        String skuId,
                                        int capacity,
                                        int requestCount,
                                        List<String> acceptedOrderIds) {
        GroupBuyGroupEntity group = groupMapper.selectById(context.getGroupId());
        List<GroupBuyOrderEntity> orders = orderMapper.selectByGroupId(context.getGroupId());
        long waitPayCount = orders.stream()
                .filter(order -> order.getStatus() == GroupOrderStatus.WAIT_PAY)
                .count();
        long rejectedCount = orders.stream()
                .filter(order -> order.getStatus() == GroupOrderStatus.REJECTED)
                .count();
        Long memberCount = memberMapper.selectCount(new QueryWrapper<GroupBuyMemberEntity>()
                .eq("group_id", context.getGroupId()));
        Long reserveLedgerCount = ledgerMapper.selectCount(new QueryWrapper<GroupBuyInventoryLedgerEntity>()
                .eq("activity_id", context.getActivityId())
                .eq("operation", "RESERVE"));
        GroupBuyInventoryStockEntity databaseStock = inventoryStockMapper.selectOne(
                new QueryWrapper<GroupBuyInventoryStockEntity>()
                        .eq("activity_id", context.getActivityId())
                        .eq("sku_id", skuId));
        Long reservationCount = inventoryReservationMapper.selectCount(
                new QueryWrapper<GroupBuyInventoryReservationEntity>()
                        .eq("activity_id", context.getActivityId())
                        .eq("status", InventoryReservationStatus.RESERVED));
        Map<String, String> redisStock = redisState(stockKey(context.getActivityId(), skuId));
        Map<String, String> redisGroup = redisState(groupKey(context.getActivityId(), skuId, context.getGroupId()));
        HttpCallResult queriedOrder = get("/api/v1/groupbuy/orders/" + acceptedOrderIds.get(0));

        log.info("预占后PostgreSQL：group={}, waitPay={}, rejected={}, members={}, reserveLedgers={}",
                group, waitPayCount, rejectedCount, memberCount, reserveLedgerCount);
        log.info("预占后Redis：stock={}, group={}", redisStock, redisGroup);
        log.info("订单查询接口抽样：{}", queriedOrder.getJson().path("data"));

        assertEquals(capacity, group.getReservedCount());
        assertEquals(capacity, waitPayCount);
        assertEquals(requestCount - capacity, rejectedCount);
        assertEquals(Long.valueOf(capacity), memberCount);
        assertEquals(Long.valueOf(capacity), reserveLedgerCount);
        assertEquals(0, databaseStock.getAvailableQuantity());
        assertEquals(capacity, databaseStock.getReservedQuantity());
        assertEquals(0, databaseStock.getConfirmedQuantity());
        assertEquals(Long.valueOf(capacity), reservationCount);
        assertEquals("0", redisStock.get("available"));
        assertEquals(String.valueOf(capacity), redisStock.get("reserved"));
        assertEquals(String.valueOf(capacity), redisGroup.get("reservedCount"));
        assertEquals("SUCCESS", queriedOrder.getCode());
        assertEquals("WAIT_PAY", queriedOrder.getJson().path("data").path("status").asText());
    }

    private void waitForGroupSuccess(Long groupId, int capacity) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            GroupBuyGroupEntity group = groupMapper.selectById(groupId);
            if (group != null && group.getStatus() == GroupInstanceStatus.SUCCESS
                    && Integer.valueOf(capacity).equals(group.getPaidCount())) {
                return;
            }
            Thread.sleep(200L);
        }
        GroupBuyGroupEntity current = groupMapper.selectById(groupId);
        throw new AssertionError("Outbox未在30秒内完成成团结算，当前团状态=" + current);
    }

    private void verifyPaidState(TestContext context,
                                 String skuId,
                                 int capacity,
                                 int requestCount) {
        GroupBuyGroupEntity group = groupMapper.selectById(context.getGroupId());
        List<GroupBuyOrderEntity> orders = orderMapper.selectByGroupId(context.getGroupId());
        long successOrderCount = orders.stream()
                .filter(order -> order.getStatus() == GroupOrderStatus.GROUP_SUCCESS)
                .count();
        long rejectedCount = orders.stream()
                .filter(order -> order.getStatus() == GroupOrderStatus.REJECTED)
                .count();
        Map<String, String> redisStock = redisState(stockKey(context.getActivityId(), skuId));
        Map<String, String> redisGroup = redisState(groupKey(context.getActivityId(), skuId, context.getGroupId()));
        GroupBuyInventoryStockEntity databaseStock = inventoryStockMapper.selectOne(
                new QueryWrapper<GroupBuyInventoryStockEntity>()
                        .eq("activity_id", context.getActivityId())
                        .eq("sku_id", skuId));
        Long confirmedReservationCount = inventoryReservationMapper.selectCount(
                new QueryWrapper<GroupBuyInventoryReservationEntity>()
                        .eq("activity_id", context.getActivityId())
                        .eq("status", InventoryReservationStatus.CONFIRMED));

        log.info("支付结算后PostgreSQL：group={}, successOrders={}, rejectedOrders={}",
                group, successOrderCount, rejectedCount);
        log.info("支付结算后Redis：stock={}, group={}", redisStock, redisGroup);

        assertEquals(GroupInstanceStatus.SUCCESS, group.getStatus());
        assertEquals(capacity, group.getReservedCount());
        assertEquals(capacity, group.getPaidCount());
        assertEquals(capacity, successOrderCount);
        assertEquals(requestCount - capacity, rejectedCount);
        assertEquals(0, databaseStock.getAvailableQuantity());
        assertEquals(0, databaseStock.getReservedQuantity());
        assertEquals(capacity, databaseStock.getConfirmedQuantity());
        assertEquals(Long.valueOf(capacity), confirmedReservationCount);
        assertEquals("0", redisStock.get("available"));
        assertEquals("0", redisStock.get("reserved"));
        assertEquals(String.valueOf(capacity), redisStock.get("confirmed"));
        assertEquals("SUCCESS", redisGroup.get("status"));
        assertEquals(String.valueOf(capacity), redisGroup.get("paidCount"));
    }

    private Map<String, String> redisState(String key) {
        RMap<String, String> map = redissonClient.getMap(key, StringCodec.INSTANCE);
        return new HashMap<>(map.readAllMap());
    }

    private void assertBatchCompleted(BatchResult batch, int expectedCount) {
        assertTrue(batch.getErrors().isEmpty(), "并发请求存在客户端异常：" + batch.getErrors());
        assertEquals(expectedCount, batch.getResults().size(), "并发请求返回数量不完整");
        assertEquals(expectedCount, batch.getResults().stream()
                .filter(result -> result.getHttpStatus() >= 200 && result.getHttpStatus() < 300)
                .count(), "存在非2xx接口响应");
    }

    private int positiveSystemProperty(String name, int defaultValue) {
        int value = Integer.getInteger(name, defaultValue);
        if (value <= 0) {
            throw new IllegalArgumentException(name + "必须大于0");
        }
        return value;
    }

    private String stockKey(Long activityId, String skuId) {
        return "groupbuy:{" + activityId + ":" + skuId + "}:stock";
    }

    private String groupKey(Long activityId, String skuId, Long groupId) {
        return "groupbuy:{" + activityId + ":" + skuId + "}:group:" + groupId;
    }

    private String textOrNull(JsonNode value) {
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    @Data
    @AllArgsConstructor
    private static class TestContext {
        private long activityId;
        private long groupId;
    }

    @Data
    @AllArgsConstructor
    private static class HttpCallResult {
        private int httpStatus;
        private String code;
        private String orderId;
        private String dataText;
        private long latencyMs;
        private String body;
        private JsonNode json;
    }

    @Data
    @AllArgsConstructor
    private static class BatchResult {
        private List<HttpCallResult> results;
        private List<String> errors;
        private long elapsedMs;

        private long count(String code) {
            return results.stream().filter(result -> code.equals(result.getCode())).count();
        }

        private long dataCount(String value) {
            return results.stream().filter(result -> value.equals(result.getDataText())).count();
        }

        private Map<String, Long> codeCounts() {
            return results.stream().collect(Collectors.groupingBy(
                    HttpCallResult::getCode, LinkedHashMap::new, Collectors.counting()));
        }

        private Map<String, Long> dataCounts() {
            return results.stream()
                    .filter(result -> result.getDataText() != null)
                    .collect(Collectors.groupingBy(
                            HttpCallResult::getDataText, LinkedHashMap::new, Collectors.counting()));
        }

        private long p95LatencyMs() {
            if (results.isEmpty()) {
                return 0L;
            }
            List<Long> latencies = results.stream()
                    .map(HttpCallResult::getLatencyMs)
                    .sorted()
                    .collect(Collectors.toList());
            int index = Math.max(0, (int) Math.ceil(latencies.size() * 0.95D) - 1);
            return latencies.get(index);
        }

        private double throughputPerSecond() {
            return elapsedMs == 0L ? results.size() : results.size() * 1000D / elapsedMs;
        }
    }
}
