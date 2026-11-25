package com.wind.integration.infrastructure.locks;

import com.wind.common.exception.BaseException;
import com.wind.common.locks.WindLock;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LockTemplateTests {

    private static final String LOCK_KEY = "order:42";

    @Test
    void testSupplierReturnsResultAndReleasesLock() {
        LocalLock lock = new LocalLock();
        LockTemplate template = new LockTemplate(key -> lock);

        String result = template.tryLock(() -> {
            assertTrue(lock.isHeldByCurrentThread());
            return "processed";
        }, LOCK_KEY, 1, 5);

        assertEquals("processed", result);
        assertFalse(lock.isLocked());
    }

    @Test
    void testRunnableFailureReleasesLock() {
        LocalLock lock = new LocalLock();
        LockTemplate template = new LockTemplate(key -> lock);
        IllegalStateException failure = new IllegalStateException("order processing failed");
        Runnable callback = () -> {
            assertTrue(lock.isHeldByCurrentThread());
            throw failure;
        };

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> template.tryLock(callback, LOCK_KEY, 1, 5)));
        assertFalse(lock.isLocked());
    }

    @Test
    void testTimeoutSkipsCallback() {
        WindLock lock = mock(WindLock.class);
        LockTemplate template = new LockTemplate(key -> lock);
        AtomicBoolean executed = new AtomicBoolean();

        assertThrows(BaseException.class, () -> template.tryLock(
                () -> executed.set(true), LOCK_KEY, 0, 5, TimeUnit.MILLISECONDS));
        assertFalse(executed.get());
    }

    @Test
    void testInterruptedAcquisitionRestoresInterruptFlag() throws InterruptedException {
        WindLock lock = mock(WindLock.class);
        when(lock.tryLock(1, 5, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        LockTemplate template = new LockTemplate(key -> lock);

        try {
            assertThrows(BaseException.class, () -> template.tryLock(() -> "unused", LOCK_KEY, 1, 5));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static final class LocalLock extends ReentrantLock implements WindLock {

        @Override
        public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
            return tryLock(waitTime, unit);
        }

        @Override
        public void lock(long leaseTime, TimeUnit unit) {
            lock();
        }
    }
}
