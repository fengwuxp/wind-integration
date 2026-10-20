package com.wind.integration.core.context;

import com.wind.trace.WindTraceContext;
import com.wind.trace.WindTracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.wind.integration.core.enums.DataIsolationDimensions.ENVIRONMENT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;
import static com.wind.integration.core.enums.DataIsolationDimensions.TEST_DATA;
import static com.wind.integration.core.enums.DataIsolationDimensions.WORKSPACE;

class DataIsolationContextHolderTests {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void testRunCreatesRootAndClearsContext() {
        DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a", ENVIRONMENT, "sit", TEST_DATA, true), () -> {
            Assertions.assertNull(WindTracer.TRACER.requireContext().parentSpanId());
            Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
            Assertions.assertEquals("ws-a", DataIsolationContextHolder.<String>requireWorkspaceId());
            Assertions.assertEquals("sit", DataIsolationContextHolder.requireEnv());
            Assertions.assertTrue(DataIsolationContextHolder.isTestData());
        });

        Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
        Assertions.assertNull(TenantContextHolder.getTenantId());
        Assertions.assertNull(DataIsolationContextHolder.getWorkspaceId());
    }

    @Test
    void testCallCreatesRootAndRestoresLegacyTenant() throws Exception {
        TenantContextHolder.setTenantId(99L);

        Long result = DataIsolationContextHolder.callWithTenantId(10L, () -> {
            Assertions.assertNull(WindTracer.TRACER.requireContext().parentSpanId());
            Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
            return DataIsolationContextHolder.requireTenantId();
        });

        Assertions.assertEquals(10L, result);
        Assertions.assertEquals(99L, TenantContextHolder.requireTenantId());
        Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
    }

    /** 在已有请求 trace 中覆盖维度，子作用域保留身份与直接父 span，退出后还原请求上下文。 */
    @Test
    void testNestedRunInheritsDimensionsAndRestoresOuterContext() {
        WindTracer.TRACER.run(() -> {
            WindTraceContext request = WindTracer.TRACER.requireContext();
            WindTracer.TRACER.putVariable("requestMarker", "outer");
            DataIsolationContextHolder.runWith(Map.of(TENANT, 10L, WORKSPACE, "ws-a", ENVIRONMENT, "sit", TEST_DATA, true), () -> {
                assertChildTrace(request);
                WindTraceContext outer = WindTracer.TRACER.requireContext();
                DataIsolationContextHolder.runWithWorkspace("ws-b", () -> {
                    assertChildTrace(outer);
                    Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
                    Assertions.assertEquals("ws-b", DataIsolationContextHolder.<String>requireWorkspaceId());
                    Assertions.assertEquals("sit", DataIsolationContextHolder.requireEnv());
                    Assertions.assertTrue(DataIsolationContextHolder.isTestData());
                    WindTracer.TRACER.putVariable("requestMarker", "inner");
                });
                Assertions.assertSame(outer, WindTracer.TRACER.requireContext());
                Assertions.assertEquals("outer", WindTracer.TRACER.getContextVariable("requestMarker"));
                Assertions.assertEquals("ws-a", DataIsolationContextHolder.<String>requireWorkspaceId());
            });
            Assertions.assertSame(request, WindTracer.TRACER.requireContext());
            Assertions.assertNull(DataIsolationContextHolder.getTenantId());
            Assertions.assertEquals(Map.of("requestMarker", "outer"), request.getContextVariables());
        });
        Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
    }

    @Test
    void testNestedCallInheritsDimensionsAndRestoresOuterContext() {
        String result = DataIsolationContextHolder.callWith(
                Map.of(TENANT, 10L, WORKSPACE, "ws-a", ENVIRONMENT, "sit", TEST_DATA, true), () -> {
                    WindTraceContext outer = WindTracer.TRACER.requireContext();
                    String workspace = DataIsolationContextHolder.callWithWorkspace("ws-b", () -> {
                        assertChildTrace(outer);
                        Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
                        Assertions.assertEquals("sit", DataIsolationContextHolder.requireEnv());
                        Assertions.assertTrue(DataIsolationContextHolder.isTestData());
                        return DataIsolationContextHolder.requireWorkspaceId();
                    });
                    Assertions.assertSame(outer, WindTracer.TRACER.requireContext());
                    Assertions.assertEquals("ws-a", DataIsolationContextHolder.<String>requireWorkspaceId());
                    Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
                    return workspace;
                });

        Assertions.assertEquals("ws-b", result);
        Assertions.assertNull(TenantContextHolder.getTenantId());
        Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
    }

    @Test
    void testRunRestoresContextWithRuntimeException() {
        IllegalStateException failure = new IllegalStateException("模拟业务失败");
        DataIsolationContextHolder.runWithTenant(10L, () -> {
            WindTraceContext outer = WindTracer.TRACER.requireContext();
            IllegalStateException actual = Assertions.assertThrows(IllegalStateException.class, () ->
                    DataIsolationContextHolder.runWithTenant(20L, () -> {
                        assertChildTrace(outer);
                        Assertions.assertEquals(20L, DataIsolationContextHolder.<Long>requireTenantId());
                        throw failure;
                    }));
            Assertions.assertSame(failure, actual);
            Assertions.assertSame(outer, WindTracer.TRACER.requireContext());
            Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
            Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
        });
        Assertions.assertNull(TenantContextHolder.getTenantId());
        Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
    }

    @Test
    void testCallRestoresContextWithCheckedException() throws Exception {
        Exception failure = new Exception("模拟受检异常");
        TenantContextHolder.setTenantId(99L);
        DataIsolationContextHolder.callWithTenantId(10L, () -> {
            WindTraceContext outer = WindTracer.TRACER.requireContext();
            RuntimeException actual = Assertions.assertThrows(RuntimeException.class, () ->
                    DataIsolationContextHolder.callWithTenantId(20L, () -> {
                        assertChildTrace(outer);
                        throw failure;
                    }));
            Assertions.assertSame(failure, actual.getCause());
            Assertions.assertSame(outer, WindTracer.TRACER.requireContext());
            Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
            Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
            return null;
        });
        Assertions.assertEquals(99L, TenantContextHolder.requireTenantId());
        Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
    }

    @Test
    void testReusedWorkerHasNoPreviousTenantWithException() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            worker.submit(() -> {
                Assertions.assertThrows(IllegalStateException.class, () ->
                        DataIsolationContextHolder.runWithTenant(10L, () -> {
                            throw new IllegalStateException("模拟任务失败");
                        }));
            }).get(5, TimeUnit.SECONDS);
            worker.submit(() -> {
                Assertions.assertNull(TenantContextHolder.getTenantId());
                Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
                DataIsolationContextHolder.callWith(Map.of(ENVIRONMENT, "sit"), () -> {
                    Assertions.assertNull(DataIsolationContextHolder.getTenantId());
                    Assertions.assertNull(DataIsolationContextHolder.getWorkspaceId());
                    return null;
                });
            }).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void testContextDoesNotPropagateToWorker() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            DataIsolationContextHolder.callWithTenantId(10L, () -> {
                worker.submit(() -> {
                    Assertions.assertNull(DataIsolationContextHolder.getTenantId());
                    Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
                }).get(5, TimeUnit.SECONDS);
                Assertions.assertEquals(10L, DataIsolationContextHolder.<Long>requireTenantId());
                return null;
            });
        }
    }

    @Test
    void testRequiredValuesRejectMissingContext() {
        Assertions.assertNull(DataIsolationContextHolder.getTenantId());
        Assertions.assertNull(DataIsolationContextHolder.getWorkspaceId());
        Assertions.assertNull(DataIsolationContextHolder.getEnv());
        Assertions.assertFalse(DataIsolationContextHolder.isTestData());
        Assertions.assertThrows(NullPointerException.class, DataIsolationContextHolder::requireTenantId);
        Assertions.assertThrows(NullPointerException.class, DataIsolationContextHolder::requireWorkspaceId);
        Assertions.assertThrows(RuntimeException.class, DataIsolationContextHolder::requireEnv);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void testRequireEnvRejectsBlankValue(String environment) {
        DataIsolationContextHolder.runWith(Map.of(ENVIRONMENT, environment), () -> {
            Assertions.assertEquals(environment, DataIsolationContextHolder.getEnv());
            Assertions.assertThrows(RuntimeException.class, DataIsolationContextHolder::requireEnv);
        });
    }

    @Test
    void testLegacyCompatibilityRequiresLongTenant() {
        TenantContextHolder.setTenantId(99L);

        Assertions.assertThrows(ClassCastException.class, () -> DataIsolationContextHolder.runWithTenant("tenant-a", () -> {
            Assertions.fail("非 Long 租户不得进入回调");
        }));
        Assertions.assertThrows(ClassCastException.class, () -> DataIsolationContextHolder.callWithTenantId("tenant-a", () -> {
            Assertions.fail("非 Long 租户不得进入回调");
            return null;
        }));

        Assertions.assertEquals(99L, TenantContextHolder.requireTenantId());
        Assertions.assertFalse(WindTracer.TRACER.currentContext().isPresent());
    }

    private void assertChildTrace(WindTraceContext parent) {
        WindTraceContext child = WindTracer.TRACER.requireContext();
        Assertions.assertEquals(parent.traceId(), child.traceId());
        Assertions.assertEquals(parent.spanId(), child.parentSpanId());
        Assertions.assertNotEquals(parent.spanId(), child.spanId());
    }
}
