package com.wind.integration.core.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.FutureTask;

class TenantContextHolderTests {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void testNestedTenantScopeRestoresPreviousTenant() throws Exception {
        TenantContextHolder.setTenantId(10L);

        Long result = TenantContextHolder.callWithTenantId(20L, () -> {
            Assertions.assertEquals(20L, TenantContextHolder.requireTenantId());
            TenantContextHolder.runWithTenantId(30L,
                    () -> Assertions.assertEquals(30L, TenantContextHolder.requireTenantId()));
            return TenantContextHolder.requireTenantId();
        });

        Assertions.assertEquals(20L, result);
        Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
    }

    @Test
    void testTenantScopeClearsAfterException() {
        Assertions.assertThrows(IllegalStateException.class, () -> TenantContextHolder.runWithTenantId(10L, () -> {
            throw new IllegalStateException("模拟业务失败");
        }));

        Assertions.assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void testCallRestoresPreviousTenantAfterException() {
        TenantContextHolder.setTenantId(10L);

        Assertions.assertThrows(Exception.class, () -> TenantContextHolder.callWithTenantId(20L, () -> {
            throw new Exception("模拟业务失败");
        }));

        Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
    }

    @Test
    void testTenantIsNotInheritedByAnotherThread() throws Exception {
        TenantContextHolder.setTenantId(10L);
        FutureTask<Long> task = new FutureTask<>(TenantContextHolder::getTenantId);
        Thread thread = new Thread(task);
        thread.start();
        thread.join();

        Assertions.assertNull(task.get());
        Assertions.assertEquals(10L, TenantContextHolder.requireTenantId());
    }
}
