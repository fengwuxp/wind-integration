package com.wind.integration.operator;

import com.wind.common.enums.DescriptiveEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务操作主体类型。
 *
 * <p>用于业务操作的审计与归因，不表示应用来源、认证方式或授权能力。</p>
 *
 * @author wuxp
 */
@AllArgsConstructor
@Getter
public enum OperationActorType implements DescriptiveEnum {

    PLATFORM_OPERATOR("平台运营人员"),

    TENANT_OPERATOR("SaaS 租户运营人员"),

    TENANT_API_CLIENT("SaaS 租户 API 客户端"),

    END_USER("C 端用户"),

    RISK_ENGINE("风控引擎"),

    SYSTEM("系统进程"),

    @Deprecated(forRemoval = true)
    OPERATIONS("平台用户"),

    @Deprecated(forRemoval = true)
    TENANT_OPERATIONS("Sass 租户用户"),

    @Deprecated(forRemoval = true)
    TENANT_API("Sass 租户 API"),

    @Deprecated(forRemoval = true)
    USER("C 端用户"),

    @Deprecated(forRemoval = true)
    RISK_CONTROL("风控"),
    ;

    private final String desc;

    /**
     * 该判断仅描述主体类型，不代表拥有任何权限。
     *
     * @return 是否为系统主体
     */
    public boolean isSystem() {
        return this == SYSTEM || this == RISK_ENGINE || this == RISK_CONTROL;
    }
}
