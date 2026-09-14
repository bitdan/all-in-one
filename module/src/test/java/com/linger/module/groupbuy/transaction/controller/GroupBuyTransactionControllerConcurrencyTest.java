package com.linger.module.groupbuy.transaction.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.linger.module.groupbuy.infrastructure.service.GroupBuyDelayScheduler;
import com.linger.module.groupbuy.infrastructure.service.GroupBuyOutboxProcessor;
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
import com.linger.module.groupbuy.inventory.model.InventoryOperation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 拼团交易接口真实并发集成测试。
 *
 * <p>测试通过代码中固定的 {@code baseUrl} 访问已部署节点，并使用 local profile 中配置的
 * PostgreSQL 和 Redis 做一致性校验。测试进程不启动 Web 服务，也不参与 Outbox 和延迟任务消费。每次运行使用
 * 唯一活动和 SKU，测试数据会保留在数据库与 Redis 中，方便执行后人工对账。</p>
 *
 * <p>性能场景按并发线程数分档，每个线程完成一次HTTP调用后继续处理下一请求，不设目标QPS。
 * 每档默认测量1000次请求，预热20次不计入统计；可通过
 * {@code groupbuy.concurrent.measure-requests}、{@code groupbuy.concurrent.warmup-requests}、
 * {@code groupbuy.concurrent.groups} 调整样本数与团数，并发档位由各方法的ValueSource指定。
 * QPS按测量请求的实际耗时计算；P95为单次HTTP调用耗时，不含尚未开始的批次任务等待时间。
 * QPS和P95仅作观测，不设达标断言，业务结果与一致性仍严格校验。多团共享同一活动及SKU，
 * baseUrl应直连单节点。库存不足测量完整HTTP拒绝路径，仍包含当前实现的数据库读写。</p>
 * <p>仅运行性能场景时使用 Maven 参数
 * {@code -Dtest=GroupBuyTransactionControllerConcurrencyTest#shouldAcceptAllOrdersForHotGroup+shouldAcceptAllOrdersAcrossGroups+shouldRejectOutOfStockOrdersQuickly}。
 * 测试依赖目标节点使用相同的PostgreSQL/Redis及已完成迁移的Schema；压测期间保留未支付订单，
 * 不同档位串行执行，建议在专用测试环境运行。</p>
 * <p>每档的待执行队列最多容纳该档的固定样本数，不随接口变慢持续增加请求。
 * HTTP整次调用默认10秒超时，批次完成等待默认120秒；可通过
 * {@code groupbuy.concurrent.http-call-timeout-ms}、{@code groupbuy.concurrent.drain-timeout-ms} 调整。
 * 发压异常后跳过后续测试，避免未完成的服务端请求影响下一档；客户端取消不代表服务端订单回滚。</p>
 */
@Slf4j
@ActiveProfiles("local")
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
        classes = LingerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.flyway.enabled=false",
                "spring.datasource.hikari.minimum-idle=20"
        }
)
class GroupBuyTransactionControllerConcurrencyTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("19.90");

    private TestRestTemplate restTemplate;
    private String baseUrl;
    private OkHttpClient httpClient;
    private String stopReason;
    private final List<String> performanceSummaries = new ArrayList<>();

    @AfterAll
    void printPerformanceSummaries() {
        if (!performanceSummaries.isEmpty()) {
            log.info("各并发档位实测汇总（包含失败档位，仅业务校验通过的档位可用于评估有效吞吐）：");
            performanceSummaries.forEach(summary -> log.info("{}", summary));
        }
    }

    @MockBean
    private GroupBuyOutboxProcessor outboxProcessor;

    @MockBean
    private GroupBuyDelayScheduler delayScheduler;

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
        assumeTrue(stopReason == null, () -> "上一场景发压异常，停止后续测试：" + stopReason);
        baseUrl = "http://43.156.83.246:9999";
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(5_000, TimeUnit.MILLISECONDS)
                .readTimeout(positiveSystemProperty("groupbuy.concurrent.http-read-timeout-ms", 10_000),
                        TimeUnit.MILLISECONDS)
                .callTimeout(positiveSystemProperty("groupbuy.concurrent.http-call-timeout-ms", 10_000),
                        TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .build();
        restTemplate = new TestRestTemplate();
        restTemplate.getRestTemplate().setRequestFactory(new OkHttp3ClientHttpRequestFactory(httpClient));
        log.info("并发测试目标节点：{}", baseUrl);
    }

    @AfterEach
    void closeHttpClient() {
        if (httpClient != null) {
            httpClient.dispatcher().cancelAll();
            httpClient.connectionPool().evictAll();
            httpClient.dispatcher().executorService().shutdownNow();
            httpClient = null;
        }
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

    @ParameterizedTest(name = "热门单团：{0} 个并发线程，观测实际QPS和P95")
    @ValueSource(ints = {5, 10, 20, 50})
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void shouldAcceptAllOrdersForHotGroup(int threads) throws Exception {
        runPerformanceScenario("热门单团", threads, 1, false);
    }

    @ParameterizedTest(name = "多团分散：{0} 个并发线程，观测实际QPS和P95")
    @ValueSource(ints = {10, 20, 50, 100})
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void shouldAcceptAllOrdersAcrossGroups(int threads) throws Exception {
        int groups = positiveSystemProperty("groupbuy.concurrent.groups", 20);
        assertTrue(groups > 1, "分散下单至少需要两个团");
        runPerformanceScenario("多团分散", threads, groups, false);
    }

    @ParameterizedTest(name = "Redis库存不足：{0} 个并发线程，观测拒绝QPS和P95")
    @ValueSource(ints = {20, 50, 100, 200})
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void shouldRejectOutOfStockOrdersQuickly(int threads) throws Exception {
        runPerformanceScenario("Redis库存不足", threads, 1, true);
    }

    private void runPerformanceScenario(String scene, int threads, int groupCount,
                                        boolean soldOut) throws Exception {
        int requests = positiveSystemProperty("groupbuy.concurrent.measure-requests", 1000);
        assertTrue(requests >= threads, "测量请求数不能少于并发线程数");
        int warmup = positiveSystemProperty("groupbuy.concurrent.warmup-requests", 20);
        int totalRequests = Math.addExact(requests, warmup);
        assertTrue(groupCount <= requests, "每个团至少应收到一次测量请求");
        int stock = soldOut ? 0 : totalRequests;
        int groupCapacity = Math.max(2, (totalRequests + groupCount - 1) / groupCount);
        String runId = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        String skuId = "PERF-" + runId;
        TestContext first = createAndPublishActivity(runId, skuId, stock, groupCapacity);
        List<TestContext> groups = new ArrayList<>();
        groups.add(first);
        for (int i = 1; i < groupCount; i++) {
            groups.add(createGroup(first.getActivityId()));
        }
        String expectedCode = soldOut ? "OUT_OF_STOCK" : "ACCEPTED";
        IntFunction<HttpCallResult> placeOrder = index -> {
            TestContext group = groups.get(index % groups.size());
            PlaceGroupOrderRequest request = new PlaceGroupOrderRequest();
            request.setRequestId("PERF-" + runId + "-" + index);
            request.setUserId(2_000_000L + index);
            request.setActivityId(group.getActivityId());
            request.setGroupId(group.getGroupId());
            request.setQuantity(1);
            return post("/api/v1/groupbuy/orders", request);
        };
        for (int i = 0; i < warmup; i++) {
            HttpCallResult result = placeOrder.apply(i);
            assertTrue(result.getHttpStatus() >= 200 && result.getHttpStatus() < 300);
            assertEquals(expectedCode, result.getCode(), "预热失败：" + result.getBody());
        }
        log.info("性能观测开始：scene={}, runId={}, activityId={}, groups={}, requests={}, threads={}",
                scene, runId, first.getActivityId(), groupCount, requests, threads);
        BatchResult batch = executeBatch(scene, requests, threads,
                index -> placeOrder.apply(warmup + index));
        String measurement = String.format(Locale.ROOT,
                "scene=%s, threads=%d, requests=%d, elapsedMs=%d, actualQps=%.2f, "
                        + "expectedCode=%s, expectedCodeQps=%.2f, expectedResponses=%d/%d, p95Ms=%d, clientErrors=%d",
                scene, threads, requests, batch.getElapsedMs(), batch.throughputPerSecond(), expectedCode,
                batch.count(expectedCode) * 1000D / Math.max(1L, batch.getElapsedMs()),
                batch.count(expectedCode), requests, batch.p95LatencyMs(), batch.getErrors().size());
        // 在业务断言之前记录，失败档位也保留实际吞吐和错误信息。
        log.info("性能观测结果：{}", measurement);
        int summaryIndex = performanceSummaries.size();
        performanceSummaries.add(measurement + ", validation=FAILED");
        assertBatchCompleted(batch, requests);
        assertEquals(requests, batch.count(expectedCode), "业务返回码不符合预期：" + batch.codeCounts());
        if (!soldOut) {
            assertEquals(requests, batch.getResults().stream().map(HttpCallResult::getOrderId)
                    .filter(id -> id != null && !id.isEmpty()).distinct().count(), "成功订单ID必须非空且唯一");
        }
        verifyPerformanceState(groups, skuId, totalRequests, soldOut);
        performanceSummaries.set(summaryIndex, measurement + ", validation=PASSED");
    }

    private void verifyPerformanceState(List<TestContext> groups, String skuId,
                                        int totalRequests, boolean soldOut) {
        long activityId = groups.get(0).getActivityId();
        int accepted = soldOut ? 0 : totalRequests;
        GroupBuyInventoryStockEntity stock = inventoryStockMapper.selectOne(
                new LambdaQueryWrapper<GroupBuyInventoryStockEntity>()
                        .eq(GroupBuyInventoryStockEntity::getActivityId, activityId)
                        .eq(GroupBuyInventoryStockEntity::getSkuId, skuId));
        assertEquals(0, stock.getAvailableQuantity());
        assertEquals(accepted, stock.getReservedQuantity());
        assertEquals(0, stock.getConfirmedQuantity());
        Map<String, String> redisStock = redisState(stockKey(activityId, skuId));
        assertEquals("0", redisStock.get("available"));
        assertEquals(String.valueOf(accepted), redisStock.get("reserved"));
        assertEquals("0", redisStock.get("confirmed"));
        assertEquals(Long.valueOf(accepted), ledgerMapper.selectCount(
                new LambdaQueryWrapper<GroupBuyInventoryLedgerEntity>()
                        .eq(GroupBuyInventoryLedgerEntity::getActivityId, activityId)
                        .eq(GroupBuyInventoryLedgerEntity::getOperation, InventoryOperation.RESERVE)));
        assertEquals(Long.valueOf(accepted), inventoryReservationMapper.selectCount(
                new LambdaQueryWrapper<GroupBuyInventoryReservationEntity>()
                        .eq(GroupBuyInventoryReservationEntity::getActivityId, activityId)
                        .eq(GroupBuyInventoryReservationEntity::getStatus, InventoryReservationStatus.RESERVED)));
        for (int i = 0; i < groups.size(); i++) {
            long groupId = groups.get(i).getGroupId();
            int groupRequests = totalRequests / groups.size() + (i < totalRequests % groups.size() ? 1 : 0);
            int groupAccepted = soldOut ? 0 : groupRequests;
            GroupBuyGroupEntity group = groupMapper.selectById(groupId);
            assertEquals(GroupInstanceStatus.OPEN, group.getStatus());
            assertEquals(groupAccepted, group.getReservedCount());
            assertEquals(String.valueOf(groupAccepted),
                    redisState(groupKey(activityId, skuId, groupId)).get("reservedCount"));
            assertEquals(Long.valueOf(groupAccepted), memberMapper.selectCount(
                    new LambdaQueryWrapper<GroupBuyMemberEntity>().eq(GroupBuyMemberEntity::getGroupId, groupId)));
            List<GroupBuyOrderEntity> orders = orderMapper.selectByGroupId(groupId);
            assertEquals(groupRequests, orders.size());
            assertEquals(groupRequests, orders.stream().filter(order -> order.getStatus()
                    == (soldOut ? GroupOrderStatus.REJECTED : GroupOrderStatus.WAIT_PAY)).count());
        }
    }

    private BatchResult executeBatch(String scene, int taskCount, int threads,
                                      IntFunction<HttpCallResult> action) {
        // 取消操作只作用于本轮客户端。
        OkHttpClient batchClient = httpClient;
        GroupBuyLoadRunner.Result<HttpCallResult> run = GroupBuyLoadRunner.run(
                taskCount, threads, taskCount, 0,
                positiveSystemProperty("groupbuy.concurrent.drain-timeout-ms", 120_000),
                positiveSystemProperty("groupbuy.concurrent.shutdown-timeout-ms", 12_000),
                (index, scheduledAt) -> action.apply(index), () -> batchClient.dispatcher().cancelAll());
        BatchResult batch = new BatchResult(run.getResponses(), run.getErrors(),
                TimeUnit.NANOSECONDS.toMillis(run.getElapsedNanos()), run.getFirstFailure());
        log.info("{}性能汇总：threads={}, planned={}, submitted={}, completed={}, responses={}, "
                        + "notSubmitted={}, unfinished={}, cancelledQueued={}, terminated={}, elapsedMs={}, "
                        + "actualQps={}, p95Ms={}, httpP95Ms={}, codes={}, errors={}",
                scene, threads, run.getPlanned(), run.getSubmitted(), run.getCompleted(), batch.getResults().size(),
                run.getPlanned() - run.getSubmitted(), run.getSubmitted() - run.getCompleted(),
                run.getCancelledQueued(), run.isTerminated(), batch.getElapsedMs(), batch.throughputPerSecond(),
                batch.p95LatencyMs(), batch.percentile95(HttpCallResult::getHttpLatencyMs),
                batch.codeCounts(), batch.getErrors().size());
        if (!run.getErrors().isEmpty() || run.getSubmitted() != taskCount || !run.isTerminated()) {
            stopReason = scene + "：" + run.getErrors().stream().limit(5).collect(Collectors.toList());
            log.error("{}发压失败，首个异常及清理异常：{}", scene, stopReason, run.getFirstFailure());
        }
        return batch;
    }

    private TestContext createAndPublishActivity(String runId, String skuId, int capacity) throws Exception {
        return createAndPublishActivity(runId, skuId, capacity, capacity);
    }

    private TestContext createAndPublishActivity(String runId, String skuId, int stock, int capacity) throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CreateActivityRequest activityRequest = new CreateActivityRequest();
        activityRequest.setName("真实并发接口测试-" + runId);
        activityRequest.setSkuId(skuId);
        activityRequest.setUnitPrice(UNIT_PRICE);
        activityRequest.setTotalStock(stock);
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

        return createGroup(activityId);
    }

    private TestContext createGroup(long activityId) {
        CreateGroupRequest groupRequest = new CreateGroupRequest();
        groupRequest.setCreatorUserId(900_000L);
        HttpCallResult groupCreated = post(
                "/api/v1/groupbuy/activities/" + activityId + "/groups", groupRequest);
        assertEquals("SUCCESS", groupCreated.getCode(), "创建拼团接口失败：" + groupCreated.getBody());
        long groupId = groupCreated.getJson().path("data").path("id").asLong();

        log.info("测试数据初始化完成：activityId={}, group={}",
                activityId, groupCreated.getJson().path("data"));
        return new TestContext(activityId, groupId);
    }

    private BatchResult executeConcurrently(String scene,
                                            int taskCount,
                                            int threadCount,
                                            IntFunction<HttpCallResult> action) throws InterruptedException {
        return executeBatch(scene, taskCount, Math.min(threadCount, taskCount), action);
    }

    private HttpCallResult post(String path, Object request) {
        long startedAt = System.nanoTime();
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + path, request, String.class);
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return parseResponse(path, response, latencyMs);
    }

    private HttpCallResult get(String path) {
        long startedAt = System.nanoTime();
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + path, String.class);
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
                    orderId, dataText, latencyMs, latencyMs, response.getBody(), json);
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
        int timeoutSeconds = positiveSystemProperty(
                "groupbuy.concurrent.settlement-timeout-seconds", 90);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            GroupBuyGroupEntity group = groupMapper.selectById(groupId);
            if (group != null && group.getStatus() == GroupInstanceStatus.SUCCESS
                    && Integer.valueOf(capacity).equals(group.getPaidCount())) {
                return;
            }
            Thread.sleep(200L);
        }
        GroupBuyGroupEntity current = groupMapper.selectById(groupId);
        throw new AssertionError("Outbox未在" + timeoutSeconds
                + "秒内完成成团结算，当前团状态=" + current);
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
        if (!batch.getErrors().isEmpty()) {
            throw new AssertionError("并发请求存在客户端异常（最多展示前10条）："
                    + batch.getErrors().stream().limit(10).collect(Collectors.toList()), batch.getFirstFailure());
        }
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
        private long httpLatencyMs;
        private String body;
        private JsonNode json;
    }

    @Data
    @AllArgsConstructor
    private static class BatchResult {
        private List<HttpCallResult> results;
        private List<String> errors;
        private long elapsedMs;
        private Throwable firstFailure;

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
            return percentile95(HttpCallResult::getLatencyMs);
        }

        private long percentile95(ToLongFunction<HttpCallResult> latency) {
            if (results.isEmpty()) {
                return 0L;
            }
            List<Long> latencies = results.stream()
                    .map(result -> latency.applyAsLong(result))
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
