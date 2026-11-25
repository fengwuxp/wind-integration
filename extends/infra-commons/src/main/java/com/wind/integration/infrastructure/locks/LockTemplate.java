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
 * 按业务键获取 {@link WindLock}，在取得锁后执行回调。适用于多个任务可能同时处理同一订单等资源的场景。
 *
 * <p>锁由 {@link LockFactory} 提供。等待超时会抛出异常并跳过回调，等待被中断时会先恢复线程中断标记。
 * 指定固定租期时，租期应覆盖回调的执行时间。</p>
 *
 * <p>工厂返回锁对象后，模板在退出时总会调用 {@link WindLock#unlock()}，包括获取锁失败的情况。
 * 接入的锁实现需要能处理当前线程未持锁时的释放调用。</p>
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
     * @param timeUnit  等待时间与租期的单位
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
