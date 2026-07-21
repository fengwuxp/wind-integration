package com.wind.integration.operator;

import com.google.common.annotations.VisibleForTesting;
import com.wind.common.exception.AssertUtils;
import com.wind.common.util.ServiceInfoUtils;
import com.wind.security.core.WindSecurityAccessOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link WindOperator} 静态工厂。
 *
 * <p>由 Spring 注入安全访问能力，统一创建当前应用或指定来源应用的操作者。</p>
 *
 * @author wuxp
 */
@Component
public final class WindOperatorFactory {

    private static final long SYSTEM_OPERATOR_ID = -1L;

    private static final AtomicReference<WindSecurityAccessOperations> ACCESS_OPERATIONS = new AtomicReference<>();

    @VisibleForTesting
    WindOperatorFactory(@NonNull WindSecurityAccessOperations accessOperations) {
        ACCESS_OPERATIONS.set(accessOperations);
    }

    /**
     * 使用当前应用作为来源创建操作者。
     *
     * @param operatorId 操作者标识
     * @param actorType  操作主体类型
     * @return 操作者
     */
    public static WindOperator current(@NonNull Long operatorId, @NonNull OperationActorType actorType) {
        return create(operatorId, null, requireCurrentApplicationName(), actorType);
    }

    /**
     * 使用当前应用作为来源创建操作者。
     *
     * @param operatorId   操作者标识
     * @param operatorName 操作者名称
     * @param actorType    操作主体类型
     * @return 操作者
     */
    public static WindOperator current(@NonNull Serializable operatorId, @NonNull String operatorName,
                                       @NonNull OperationActorType actorType) {
        return create(operatorId, operatorName, requireCurrentApplicationName(), actorType);
    }

    /**
     * 使用指定来源应用创建操作者。
     *
     * @param operatorId        操作者标识
     * @param sourceApplication 来源应用名称
     * @param actorType         操作主体类型
     * @return 操作者
     */
    public static WindOperator fromApplication(@NonNull Serializable operatorId, @NonNull String sourceApplication,
                                               @NonNull OperationActorType actorType) {
        return create(operatorId, null, sourceApplication, actorType);
    }

    /**
     * 使用指定来源应用创建操作者。
     *
     * @param operatorId        操作者标识
     * @param operatorName      操作者名称
     * @param sourceApplication 来源应用名称
     * @param actorType         操作主体类型
     * @return 操作者
     */
    public static WindOperator fromApplication(@NonNull Long operatorId, @NonNull String operatorName,
                                               @NonNull String sourceApplication,
                                               @NonNull OperationActorType actorType) {
        return create(operatorId, operatorName, sourceApplication, actorType);
    }

    /**
     * @return 当前应用的系统操作者
     */
    public static WindOperator system() {
        return current(SYSTEM_OPERATOR_ID, OperationActorType.SYSTEM.name(), OperationActorType.SYSTEM);
    }

    /**
     * @param riskSourceId 风控来源于标识
     * @return 当前应用的风控引擎操作者
     */
    public static WindOperator riskEngine(@NonNull Serializable riskSourceId) {
        return current(riskSourceId, OperationActorType.RISK_ENGINE.name(), OperationActorType.RISK_ENGINE);
    }

    /**
     * 创建操作者。
     *
     * @param operatorId        操作者标识
     * @param operatorName      操作者名称
     * @param sourceApplication 来源应用名称
     * @param actorType         操作主体类型
     * @return 操作者
     */
    public static WindOperator create(Serializable operatorId, @Nullable String operatorName, String sourceApplication, OperationActorType actorType) {
        return WindOperator.builder()
                .operatorId(operatorId)
                .operatorName(operatorName)
                .appName(sourceApplication)
                .actorType(actorType)
                .accessOperations(requireSecurityAccessOperations())
                .build();
    }

    private static String requireCurrentApplicationName() {
        String result = ServiceInfoUtils.getApplicationName();
        AssertUtils.hasText(result, "spring.application.name must not be blank");
        return result;
    }

    private static WindSecurityAccessOperations requireSecurityAccessOperations() {
        WindSecurityAccessOperations result = ACCESS_OPERATIONS.get();
        AssertUtils.notNull(result, "WindSecurityAccessOperations must not be null");
        return result;
    }
}
