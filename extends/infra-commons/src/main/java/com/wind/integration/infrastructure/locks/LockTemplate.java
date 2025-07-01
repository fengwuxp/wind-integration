package com.wind.integration.infrastructure.locks;

import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.locks.LockFactory;
import com.wind.common.locks.WindLock;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


/**
 * 以加锁的方式执行操作
 */
@Slf4j
@AllArgsConstructor
public class LockTemplate {

    private final LockFactory lockFactory;

    /**
     * 方法加锁执行
     *
     * @param run       执行的方法
     * @param lockKey   锁 Key
     * @param waitTime  获取锁的等待时间/S
     * @param leaseTime 锁的存活时间/S
     */
    public void tryLock(Runnable run, String lockKey, Integer waitTime, Integer leaseTime) {
        tryLock(run, lockKey, waitTime, leaseTime, TimeUnit.SECONDS);
    }

    /**
     * 方法加锁执行
     *
     * @param run       执行的方法
     * @param lockKey   锁 Key
     * @param waitTime  获取锁的等待时间
     * @param leaseTime 锁的存活时间
     * @param timeUnit  等待时间的单位
     */
    public void tryLock(Runnable run, String lockKey, Integer waitTime, Integer leaseTime, TimeUnit timeUnit) {
        WindLock lock = null;
        try {
            lock = lockFactory.apply(lockKey);
            AssertUtils.state(lock.tryLock(waitTime, leaseTime, timeUnit), () -> BaseException.friendly("try lock = " + lockKey + " failure"));
            run.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw BaseException.friendly(String.format("lock key = %s lock interrupt", lockKey));
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    /**
     * 方法加锁执行
     *
     * @param run       执行的方法
     * @param lockKey   锁 Key
     * @param waitTime  等待时间/S
     * @param leaseTime 锁时间/S
     */
    public <T> T tryLock(Supplier<T> run, String lockKey, Integer waitTime, Integer leaseTime) {
        WindLock lock = null;
        try {
            lock = lockFactory.apply(lockKey);
            AssertUtils.state(lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS), () -> BaseException.friendly("try lock = " + lockKey + " failure"
            ));
            return run.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BaseException.friendly(String.format("lock key = %s lock interrupt", lockKey));
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }
}
