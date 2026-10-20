package com.wind.integration.core.enums;

import com.wind.integration.core.model.EnvIsolationObject;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.integration.core.model.WorkspaceIsolationObject;
import lombok.Getter;
import org.jspecify.annotations.NullMarked;

import static com.wind.common.WindConstants.ISOLATION_ENV_ATTRIBUTE_NAME;
import static com.wind.common.WindConstants.ISOLATION_TENANT_ID_ATTRIBUTE_NAME;
import static com.wind.common.WindConstants.ISOLATION_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME;
import static com.wind.common.WindConstants.ISOLATION_WORKSPACE_ID_ATTRIBUTE_NAME;

/**
 * 内置读取隔离维度，绑定各自的实体归属接口，用于配置规则和临时忽略。
 * 各维度接口独立，枚举本身不保存范围或忽略状态。
 *
 * @author wuxp
 * @since 2026-10-19
 */
@NullMarked
public enum DataIsolationDimensions {

    TENANT(TenantIsolationObject.class, ISOLATION_TENANT_ID_ATTRIBUTE_NAME),

    WORKSPACE(WorkspaceIsolationObject.class, ISOLATION_WORKSPACE_ID_ATTRIBUTE_NAME),

    ENVIRONMENT(EnvIsolationObject.class, ISOLATION_ENV_ATTRIBUTE_NAME),

    TEST_DATA(TestDataIsolationObject.class, ISOLATION_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME);

    private final Class<?> isolationType;

    @Getter
    private final String traceAttributeName;

    DataIsolationDimensions(Class<?> isolationType, String traceAttributeName) {
        this.isolationType = isolationType;
        this.traceAttributeName = traceAttributeName;
    }

    /**
     * 获取用于匹配实体的隔离归属接口，支持实体间接实现。
     *
     * @return 当前维度对应的实体接口
     */
    public Class<?> isolationType() {
        return isolationType;
    }
}
