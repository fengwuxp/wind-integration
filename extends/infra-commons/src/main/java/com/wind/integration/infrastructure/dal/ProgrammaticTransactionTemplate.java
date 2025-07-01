package com.wind.integration.infrastructure.dal;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.validation.constraints.NotNull;
import java.util.function.Supplier;

/**
 * 编程式事务调用
 */
@AllArgsConstructor
@Slf4j
public class ProgrammaticTransactionTemplate {

    private final PlatformTransactionManager platformTransactionManager;

    /**
     * 编程式事务调用
     *
     * @param function 事务执行函数
     */
    public void execute(Runnable function) {
        execute(function, Propagation.REQUIRED);
    }

    /**
     * 编程式事务调用
     *
     * @param function    事务执行函数
     * @param propagation 事务传播行为
     * @see Propagation
     */
    @SuppressWarnings("java:S1181")
    public void execute(Runnable function, Propagation propagation) {
        DefaultTransactionDefinition defaultTransactionDefinition = new DefaultTransactionDefinition();
        defaultTransactionDefinition.setPropagationBehavior(propagation.value());
        TransactionStatus transaction = this.platformTransactionManager.getTransaction(defaultTransactionDefinition);

        try {
            function.run();
            this.platformTransactionManager.commit(transaction);
        } catch (Throwable e) {
            try {
                this.platformTransactionManager.rollback(transaction);
            } catch (Throwable ee) {
                log.error("transaction rollback error,message:{}", ee.getMessage(), ee);
            }
            throw e;
        }
    }

    /**
     * 编程式事务调用
     *
     * @param supplier 事务执行函数
     */
    public <T> T execute(Supplier<T> supplier) {
        return execute(supplier, Propagation.REQUIRED, null);
    }

    /**
     * 编程式事务调用
     *
     * @param supplier    事务执行函数
     * @param propagation 事务传播行为
     */
    public <T> T execute(Supplier<T> supplier, Propagation propagation) {
        return execute(supplier, propagation, null);
    }

    /**
     * @param supplier 事务执行函数
     * @param fallback 降级处理
     * @return 返回内容
     */
    public <T> T execute(Supplier<T> supplier, @NotNull Supplier<T> fallback) {
        return execute(supplier, Propagation.REQUIRED, fallback);
    }

    /**
     * 编程式事务调用
     *
     * @param supplier    事务执行函数
     * @param propagation 事务传播行为
     * @param fallback    降级处理
     * @see Propagation
     */
    @SuppressWarnings("java:S1181")
    public <T> T execute(Supplier<T> supplier, Propagation propagation, @Nullable Supplier<T> fallback) {
        DefaultTransactionDefinition defaultTransactionDefinition = new DefaultTransactionDefinition();
        // TODO 增加事物名称、超时配置等
        defaultTransactionDefinition.setPropagationBehavior(propagation.value());
        TransactionStatus transaction = this.platformTransactionManager.getTransaction(defaultTransactionDefinition);
        try {
            T result = supplier.get();
            this.platformTransactionManager.commit(transaction);
            return result;
        } catch (Throwable e) {
            try {
                this.platformTransactionManager.rollback(transaction);
            } catch (Throwable ee) {
                log.error("transaction rollback error,message:{}", ee.getMessage(), ee);
            }
            if (fallback == null) {
                throw e;
            }
            return fallback.get();
        }
    }

}
