package com.wind.integration.operator;

import com.wind.security.core.WindSecurityAccessOperations;
import com.wind.trace.WindTracer;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

import static com.wind.common.WindHttpConstants.HTTP_REQUEST_CLIENT_ID_HEADER_NAME;
import static com.wind.common.WindHttpConstants.HTTP_REQUEST_IP_ATTRIBUTE_NAME;
import static com.wind.common.WindHttpConstants.HTTP_USER_AGENT_HEADER_NAME;

/**
 * 业务操作的操作者上下文。
 *
 * <p>记录发起操作的主体标识、主体类型和来源应用，供应用服务进行操作归因、
 * 应用分支以及角色和权限判断。角色和权限判断委托给 {@link WindSecurityAccessOperations}，
 * 请求来源和设备信息按需从当前链路上下文读取。</p>
 *
 * <p>该对象不承载业务状态或领域规则。</p>
 *
 * @author wuxp
 */
@Getter
@ToString
@Builder
public final class WindOperator implements WindSecurityAccessOperations {

    /**
     * 操作者标识。
     */
    @NonNull
    private final Serializable operatorId;

    /**
     * 操作主体类型。
     */
    @NonNull
    private final OperationActorType actorType;

    /**
     * 操作者名称。
     */
    @Nullable
    private final String operatorName;

    /**
     * 操作者所属应用。
     */
    @NonNull
    private final String appName;

    /**
     * 安全访问操作代理。
     */
    @Getter(AccessLevel.NONE)
    @ToString.Exclude
    @NonNull
    private final WindSecurityAccessOperations accessOperations;

    @SuppressWarnings("unchecked")
    @NonNull
    public <T> T getOperatorId() {
        return (T) operatorId;
    }

    @NonNull
    public String getOperatorAsText() {
        return String.valueOf(operatorId);
    }

    @Override
    public boolean hasAnyAuthority(String... authorities) {
        return accessOperations.hasAnyAuthority(authorities);
    }

    @Override
    public boolean hasAnyRole(String... roles) {
        return accessOperations.hasAnyRole(roles);
    }

    @Override
    public boolean isSuperAdmin() {
        return accessOperations.isSuperAdmin();
    }

    /**
     * @return 是否是系统操作
     */
    public boolean isSystem() {
        return actorType.isSystem();
    }

    /**
     * @return 是否为 C 端用户操作
     */
    public boolean isEndUser() {
        return actorType == OperationActorType.END_USER;
    }

    /**
     * @return 请求来源 IP
     */
    @Nullable
    public String getRequestSourceIp() {
        return WindTracer.TRACER.getContextVariable(HTTP_REQUEST_IP_ATTRIBUTE_NAME);
    }

    /**
     * @return 请求客户端设备标识
     */
    @Nullable
    public String getRequestDeviceId() {
        return WindTracer.TRACER.getContextVariable(HTTP_REQUEST_CLIENT_ID_HEADER_NAME);
    }

    /**
     * @return 请求客户端设备代理
     */
    @Nullable
    public String getRequestDeviceUserAgent() {
        return WindTracer.TRACER.getContextVariable(HTTP_USER_AGENT_HEADER_NAME);
    }

    /**
     * @param appName 应用名称
     * @return 是否是指定应用
     */
    public boolean isApp(@NonNull String appName) {
        return Objects.equals(this.appName, appName);
    }

}
