package com.wind.integration.core.context;

import com.wind.trace.task.ContextPropagationTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.FutureTask;
import java.util.Map;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

class DataIsolationQueryIgnoreHolderTests {

    @AfterEach
    void tearDown() {
        DataIsolationQueryIgnoreHolder.clear();
        TenantContextHolder.clear();
    }

    @Test
    void testMultipleIgnoredDimensionsRestoreAfterCheckedException() {
        DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
            Assertions.assertThrows(Exception.class, () -> DataIsolationQueryIgnoreHolder.callIgnoring(() -> {
                Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
                Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
                Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TEST_DATA));
                throw new Exception("模拟业务失败");
            }, TENANT, ENVIRONMENT, TENANT));
            Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
            Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
        }, ENVIRONMENT);

        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
    }

    @Test
    void testEmptyIgnoredDimensionsKeepExistingIsolation() {
        DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
                Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
                Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TEST_DATA));
            });
            Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
        }, ENVIRONMENT);

        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
    }

    @Test
    void testNestedIgnoreRestoresEachDimensionAfterException() {
        DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
            Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
            Assertions.assertThrows(IllegalStateException.class, () ->
                    DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                        Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
                        Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(TEST_DATA));
                        throw new IllegalStateException("模拟业务失败");
                    }, TEST_DATA));
            Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
            Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TEST_DATA));
        }, ENVIRONMENT);

        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TEST_DATA));
    }

    @Test
    void testTenantAndIgnoredDimensionsAreClearedIndependently() {
        TenantContextHolder.runWithTenantId(10L, () -> {
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                DataIsolationQueryIgnoreHolder.clear();
                Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
                Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
            }, ENVIRONMENT);
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                TenantContextHolder.clear();
                Assertions.assertNull(TenantContextHolder.getTenantId());
                Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
            }, ENVIRONMENT);
        });
        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
    }

    @Test
    void testIgnoringCallReturnsValueAndRestoresIgnoredDimensions() throws Exception {
        TenantContextHolder.setTenantId(10L);

        Long tenantId = DataIsolationQueryIgnoreHolder.callIgnoring(() -> {
            Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
            return TenantContextHolder.requireTenantId();
        }, TENANT);

        Assertions.assertEquals(10L, tenantId);
        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
        Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
    }

    @Test
    void testIgnoredDimensionsAreNotInheritedByAnotherThread() throws Exception {
        boolean ignoredByChild = DataIsolationQueryIgnoreHolder.callIgnoring(() -> {
            FutureTask<Boolean> task = new FutureTask<>(() -> DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
            Thread thread = new Thread(task);
            thread.start();
            thread.join();
            Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
            return task.get();
        }, ENVIRONMENT);

        Assertions.assertFalse(ignoredByChild);
        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(ENVIRONMENT));
    }

    @Test
    void testIgnoringDoesNotChangeBoundDimensions() {
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a"), () -> {
            DataIsolationQueryIgnoreHolder.runIgnoring(() -> {
                Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
                Assertions.assertEquals("ws-a", DataIsolationContextHolder.<String>requireWorkspaceId());
                Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
            }, TENANT);
            Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
        });
    }

    /** 显式传播 trace 时只带走归属值，查询忽略仍留在原线程的回调中。 */
    @Test
    void testTracePropagationDoesNotCopyIgnoredDimensions() throws Exception {
        boolean childIgnored = DataIsolationContextHolder.callWithTenantId(10L, () ->
                DataIsolationQueryIgnoreHolder.callIgnoring(() -> {
                    FutureTask<Boolean> task = new FutureTask<>(() -> {
                        Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
                        return DataIsolationQueryIgnoreHolder.isIgnored(TENANT);
                    });
                    Thread thread = new Thread(ContextPropagationTaskDecorator.of().decorate(task));
                    thread.start();
                    thread.join();
                    Assertions.assertTrue(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
                    return task.get();
                }, TENANT));

        Assertions.assertFalse(childIgnored);
        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
    }

    @Test
    void testIgnoringPreservesCheckedException() {
        Exception failure = new Exception("模拟受检异常");

        Exception actual = Assertions.assertThrows(Exception.class, () -> DataIsolationQueryIgnoreHolder.callIgnoring(() -> {
            throw failure;
        }, TENANT));

        Assertions.assertSame(failure, actual);
        Assertions.assertFalse(DataIsolationQueryIgnoreHolder.isIgnored(TENANT));
    }
}
