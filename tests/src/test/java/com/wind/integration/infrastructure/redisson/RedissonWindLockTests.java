package com.wind.integration.infrastructure.redisson;

import com.wind.common.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedissonWindLockTests {

    private final RLock redisLock = mock(RLock.class);

    private RedissonWindLock lock;

    @BeforeEach
    void setup() {
        RedissonClient client = mock(RedissonClient.class);
        when(client.getLock("order:42")).thenReturn(redisLock);
        lock = new RedissonWindLock(client, "order:42");
    }

    @Test
    void testTryLockWithWaitAndLease() throws InterruptedException {
        when(redisLock.tryLock(2, 15, TimeUnit.SECONDS)).thenReturn(true, false);

        assertTrue(lock.tryLock(2, 15, TimeUnit.SECONDS));
        assertFalse(lock.tryLock(2, 15, TimeUnit.SECONDS));
    }

    @Test
    void testUnlockAfterLeaseExpires() {
        when(redisLock.isHeldByCurrentThread()).thenReturn(false);
        doThrow(new IllegalMonitorStateException()).when(redisLock).unlock();

        assertDoesNotThrow(lock::unlock);
    }

    @Test
    void testUnlockPreservesRedisFailureWhenHeld() {
        IllegalStateException failure = new IllegalStateException("Redis unavailable");
        when(redisLock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(failure).when(redisLock).unlock();

        BaseException exception = assertThrows(BaseException.class, lock::unlock);
        assertSame(failure, exception.getCause());
    }
}
