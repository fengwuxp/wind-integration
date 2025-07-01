package com.wind.integration.infrastructure.redisson;

import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.common.locks.WindLock;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * redisson WindLock 实现
 *
 * @author wuxp
 * @date 2025-06-11 11:09
 **/
@Slf4j
public class RedissonWindLock implements WindLock {

    private final RLock lock;

    public RedissonWindLock(RedissonClient client, String lockName) {
        this.lock = client.getLock(lockName);
    }

    @Override
    public boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException {
        return lock.tryLock(waitTime, leaseTime, unit);
    }

    @Override
    public void lock(long leaseTime, TimeUnit unit) {
        lock.lock(leaseTime, unit);
    }

    @Override
    public void lock() {
        lock.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        lock.lockInterruptibly();
    }

    @Override
    public boolean tryLock() {
        return lock.tryLock();
    }

    @Override
    public boolean tryLock(long time, @NotNull TimeUnit unit) throws InterruptedException {
        return lock.tryLock(time, unit);
    }

    @Override
    public void unlock() {
        if (lock.isHeldByCurrentThread()) {
            try {
                lock.unlock();
            } catch (Exception exception) {
                log.error("unlock exception, lock key = {}", lock.getName());
                throw new BaseException(DefaultExceptionCode.COMMON_FRIENDLY_ERROR, exception.getMessage(), exception);
            }
        }
    }

    @NotNull
    @Override
    public Condition newCondition() {
        return lock.newCondition();
    }
}