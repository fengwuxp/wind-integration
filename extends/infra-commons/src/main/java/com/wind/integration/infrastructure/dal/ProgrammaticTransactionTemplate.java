package com.wind.integration.infrastructure.dal;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.function.Supplier;

/**
 * 编程式事务模板操作
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
        } catch (Throwable throwable) {
            try {
                this.platformTransactionManager.rollback(transaction);
            } catch (Throwable exception) {
                log.error("transaction rollback error, message = {}", exception.getMessage(), exception);
            }
            throw throwable;
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
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        // TODO 增加事物名称、超时配置等
        definition.setPropagationBehavior(propagation.value());
        TransactionStatus transaction = this.platformTransactionManager.getTransaction(definition);
        try {
            T result = supplier.get();
            this.platformTransactionManager.commit(transaction);
            return result;
        } catch (Throwable throwable) {
            try {
                this.platformTransactionManager.rollback(transaction);
            } catch (Throwable exception) {
                log.error("transaction rollback error, message = {}", exception.getMessage(), exception);
            }
            if (fallback == null) {
                throw throwable;
            }
            return fallback.get();
        }
    }

}
