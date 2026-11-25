package com.wind.integration.infrastructure.redisson;

import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.common.locks.WindLock;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * 基于 Redisson {@link RLock} 的 {@link WindLock} 实现。每个实例绑定一个锁名，
 * 适合由锁工厂按资源键创建，让访问同一 Redis 的应用实例按相同锁名互斥执行。
 *
 * <p>等待时间和租期由底层锁执行。释放前检查当前线程是否仍持锁，获取失败或租期届满后，
 * 当前线程已无持有权时会跳过释放。</p>
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
    public boolean tryLock(long time, @NonNull TimeUnit unit) throws InterruptedException {
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

    @NonNull
    @Override
    public Condition newCondition() {
        return lock.newCondition();
    }
}
