package com.linger.module.groupbuy.transaction.controller;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class GroupBuyLoadRunnerTest {

    @Test
    void shouldCompleteEveryRequestAndTerminate() {
        GroupBuyLoadRunner.Result<Integer> result = GroupBuyLoadRunner.run(
                20, 2, 20, 0, 1000, 1000, (index, scheduledAt) -> index, () -> { });

        assertEquals(20, result.getSubmitted());
        assertEquals(20, result.getCompleted());
        assertEquals(20, result.getResponses().stream().distinct().count());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.isTerminated());
    }

    @Test
    void shouldKeepClientFailureWhenCleanupAlsoFails() {
        IllegalStateException original = new IllegalStateException("HTTP failed");
        IllegalStateException cleanup = new IllegalStateException("cancel failed");
        GroupBuyLoadRunner.Result<Integer> result = GroupBuyLoadRunner.run(
                1, 1, 1, 0, 1000, 1000,
                (index, scheduledAt) -> { throw original; }, () -> { throw cleanup; });

        assertSame(original, result.getFirstFailure());
        assertSame(cleanup, original.getSuppressed()[0]);
        assertEquals(1, result.getCompleted());
        assertTrue(result.getResponses().isEmpty());
        assertTrue(result.isTerminated());
    }

    @Test
    void shouldStopSubmittingWhenQueueIsFullAndKeepPartialStatistics() {
        Semaphore release = new Semaphore(0);
        try {
            GroupBuyLoadRunner.Result<Integer> result = GroupBuyLoadRunner.run(
                    100, 1, 1, 0, 50, 1000,
                    (index, scheduledAt) -> { release.acquireUninterruptibly(); return index; },
                    release::release);

            assertTrue(result.getSubmitted() < result.getPlanned());
            assertTrue(result.getSubmitted() <= 2, "最多一个运行任务和一个排队任务");
            assertTrue(result.getFirstFailure().getMessage().contains("CLIENT_QUEUE_FULL"));
            assertEquals(0, result.getCompleted(), "取消后的响应不能混入测量快照");
            assertTrue(result.getResponses().isEmpty());
            assertTrue(result.isTerminated());
        } finally {
            release.release(100);
        }
    }

    @Test
    void shouldKeepBatchTimeoutWhenWorkerDoesNotExit() throws Exception {
        Semaphore release = new Semaphore(0);
        CountDownLatch workerExited = new CountDownLatch(1);
        try {
            GroupBuyLoadRunner.Result<Integer> result = GroupBuyLoadRunner.run(
                    1, 1, 1, 0, 100, 20, (index, scheduledAt) -> {
                        try {
                            release.acquireUninterruptibly();
                            return index;
                        } finally {
                            workerExited.countDown();
                        }
                    }, () -> { });

            assertTrue(result.getFirstFailure().getMessage().contains("BATCH_TIMEOUT"));
            assertTrue(result.getFirstFailure().getSuppressed()[0].getMessage().contains("CLEANUP_TIMEOUT"));
            assertFalse(result.isTerminated());
            assertEquals(0, result.getCompleted());
        } finally {
            release.release();
            assertTrue(workerExited.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void shouldCancelBlockedHttpCallAndTerminateWorker() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            server.start();
            TestRestTemplate template = new TestRestTemplate();
            template.getRestTemplate().setRequestFactory(new OkHttp3ClientHttpRequestFactory(client));
            GroupBuyLoadRunner.Result<String> result = GroupBuyLoadRunner.run(
                    1, 1, 1, 0, 500, 2000,
                    (index, scheduledAt) -> template.getForObject("http://127.0.0.1:" + server.getPort() + "/orders", String.class),
                    () -> client.dispatcher().cancelAll());

            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS));
            assertTrue(result.getFirstFailure().getMessage().contains("BATCH_TIMEOUT"));
            assertTrue(result.isTerminated(), "取消必须能结束真实的阻塞HTTP读取");
            assertTrue(result.getResponses().isEmpty());
        } finally {
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
    }

    @Test
    void shouldBoundWholeHttpCallBeforeBatchDeadline() throws Exception {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(200, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            server.start();
            TestRestTemplate template = new TestRestTemplate();
            template.getRestTemplate().setRequestFactory(new OkHttp3ClientHttpRequestFactory(client));
            GroupBuyLoadRunner.Result<String> result = GroupBuyLoadRunner.run(
                    1, 1, 1, 0, 2000, 1000,
                    (index, scheduledAt) -> template.getForObject("http://127.0.0.1:" + server.getPort() + "/orders", String.class),
                    () -> client.dispatcher().cancelAll());

            assertEquals(1, result.getCompleted(), "HTTP自身应超时返回，而不是等批次超时后才取消");
            assertTrue(result.getResponses().isEmpty());
            assertEquals(1, result.getErrors().size());
            assertTrue(result.isTerminated());
        } finally {
            client.dispatcher().cancelAll();
            client.connectionPool().evictAll();
            client.dispatcher().executorService().shutdownNow();
        }
    }
}
