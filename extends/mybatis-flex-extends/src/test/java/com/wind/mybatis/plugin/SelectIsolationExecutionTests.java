package com.wind.mybatis.plugin;

import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.isolation.DataIsolationStrategy.FieldRule;
import com.wind.mybatis.isolation.FieldDataIsolationStrategy;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实解析器验证执行器复用、排队超时、拒绝、中断和恢复，不依赖解析耗时阈值。
 */
@Timeout(20)
class SelectIsolationExecutionTests {

    /**
     * 多个重写器的真实解析任务共享有界资源，不能随 Mapper 元数据重建而累积常驻线程。
     */
    @Test
    void testDefaultParserPoolIsSharedAcrossRewriters() {
        Configuration configuration = configuration();
        MappedStatement statement = statement(configuration);
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                FieldRule.forDimension(TENANT, "tenantId", () -> DataIsolationScope.of(10L))));
        int threadLimit = Math.max(4, Runtime.getRuntime().availableProcessors());

        for (int i = 0; i <= threadLimit; i++) {
            SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration, fields);
            assertTenant(rewriter.rewrite(statement, statement.getBoundSql(null)), 10L);
        }

        List<Thread> parserThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isAlive() && thread.getName().startsWith("wind-isolation-parser-"))
                .toList();
        assertFalse(parserThreads.isEmpty());
        assertTrue(parserThreads.size() <= threadLimit, "解析线程数不能随重写器数量持续增长");
        assertTrue(parserThreads.stream().allMatch(Thread::isDaemon));
    }

    @Test
    void testRepeatedQueriesReuseBorrowedExecutor() {
        AtomicInteger created = new AtomicInteger();
        try (ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            created.incrementAndGet();
            return Thread.ofPlatform().daemon(true).unstarted(task);
        })) {
            Configuration configuration = configuration();
            SelectIsolationRewriter rewriter = rewriter(configuration, executor, () -> 10L);
            MappedStatement statement = statement(configuration);
            for (int i = 0; i < 30; i++) {
                assertTenant(rewriter.rewrite(statement, statement.getBoundSql(null)), 10L);
            }
            assertEquals(1, created.get());
            assertFalse(executor.isShutdown());
        }
    }

    @Test
    void testSaturatedExecutorRejectsQueryAndRecovers() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        ThreadPoolExecutor executor = executor(completed);
        try {
            blockWorker(executor, release);
            executor.submit(() -> await(release));
            Configuration configuration = configuration();
            SelectIsolationRewriter rewriter = rewriter(configuration, executor, () -> 10L);
            MappedStatement statement = statement(configuration);
            try {
                IllegalStateException error = assertThrows(IllegalStateException.class,
                        () -> rewriter.rewrite(statement, statement.getBoundSql(null)));
                assertInstanceOf(RejectedExecutionException.class, rootCause(error));
            } finally {
                release.countDown();
            }
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertTenant(rewriter.rewrite(statement, statement.getBoundSql(null)), 10L);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void testQueuedParseTimesOutAndExecutorRemainsUsable() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        ThreadPoolExecutor executor = executor(completed);
        try {
            blockWorker(executor, release);
            Configuration configuration = configuration();
            SelectIsolationRewriter rewriter = rewriter(configuration, executor, () -> 10L);
            MappedStatement statement = statement(configuration);
            try {
                IllegalStateException error = assertThrows(IllegalStateException.class,
                        () -> rewriter.rewrite(statement, statement.getBoundSql(null)));
                assertInstanceOf(TimeoutException.class, rootCause(error));
                assertFalse(Thread.currentThread().isInterrupted());
            } finally {
                release.countDown();
            }
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertTenant(rewriter.rewrite(statement, statement.getBoundSql(null)), 10L);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void testInterruptedCallerKeepsInterruptAndCancelsQueuedParse() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        ThreadPoolExecutor executor = executor(completed);
        try {
            blockWorker(executor, release);
            Configuration configuration = configuration();
            SelectIsolationRewriter rewriter = rewriter(configuration, executor, () -> 10L);
            MappedStatement statement = statement(configuration);
            try {
                Thread.currentThread().interrupt();
                IllegalStateException error = assertThrows(IllegalStateException.class,
                        () -> rewriter.rewrite(statement, statement.getBoundSql(null)));
                assertInstanceOf(InterruptedException.class, rootCause(error));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
                release.countDown();
            }
            assertTrue(completed.await(3, TimeUnit.SECONDS));
            assertTenant(rewriter.rewrite(statement, statement.getBoundSql(null)), 10L);
        } finally {
            Thread.interrupted();
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void testConcurrentRewritesUseEachCallerScope() throws Exception {
        ThreadLocal<Long> tenants = new ThreadLocal<>();
        Configuration configuration = configuration();
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                new FieldRule(SelectIsolationRewriterTests.ProtectedRecord.class, "tenantId", () -> DataIsolationScope.of(tenants.get()))));
        SelectIsolationRewriter rewriter = new SelectIsolationRewriter(configuration, fields);
        MappedStatement statement = statement(configuration);
        try (ExecutorService callers = Executors.newFixedThreadPool(4)) {
            List<Future<?>> results = new ArrayList<>();
            for (long tenant = 10; tenant < 14; tenant++) {
                long currentTenant = tenant;
                results.add(callers.submit(() -> {
                    tenants.set(currentTenant);
                    try {
                        for (int i = 0; i < 30; i++) {
                            assertTenant(rewriter.rewrite(statement, statement.getBoundSql(null)), currentTenant);
                        }
                    } finally {
                        tenants.remove();
                    }
                }));
            }
            for (var result : results) {
                result.get(5, TimeUnit.SECONDS);
            }
        }
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.addMapper(SelectIsolationRewriterTests.ProtectedMapper.class);
        return configuration;
    }

    private SelectIsolationRewriter rewriter(Configuration configuration, ExecutorService executor, Supplier<Long> tenants) {
        FieldDataIsolationStrategy fields = new FieldDataIsolationStrategy(List.of(
                new FieldRule(SelectIsolationRewriterTests.ProtectedRecord.class, "tenantId", () -> DataIsolationScope.of(tenants.get()))));
        return new SelectIsolationRewriter(configuration, executor, fields);
    }

    private MappedStatement statement(Configuration configuration) {
        return new MappedStatement.Builder(configuration, "test.execution",
                new StaticSqlSource(configuration, "SELECT id FROM protected_records"), SqlCommandType.SELECT).build();
    }

    private ThreadPoolExecutor executor(CountDownLatch completed) {
        return new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                Thread.ofPlatform().daemon(true).factory(), new ThreadPoolExecutor.AbortPolicy()) {
            @Override
            protected void afterExecute(Runnable task, Throwable error) {
                completed.countDown();
                super.afterExecute(task, error);
            }
        };
    }

    private void blockWorker(ExecutorService executor, CountDownLatch release) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            await(release);
        });
        assertTrue(started.await(3, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private static Throwable rootCause(Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }

    private static void assertTenant(BoundSql sql, Long expected) {
        assertTrue(sql.getSql().contains("tenant_id"));
        assertEquals(1, sql.getParameterMappings().size());
        assertEquals(expected, sql.getAdditionalParameter(sql.getParameterMappings().getFirst().getProperty()));
    }
}
