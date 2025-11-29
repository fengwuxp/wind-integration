package com.wind.integration.core.meatadata;

import com.wind.common.enums.DescriptiveEnum;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.integration.core.resources.TreeResources;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * wind-system 资源定义
 *
 * @author wuxp
 * @date 2025-11-26 17:27
 **/
public interface WindSystemResourceDefinition extends TreeResources<Long>, TenantIsolationObject<Long> {

    /**
     * 例如：应用的 appName
     *
     * @return 资源所属者
     */
    @NotBlank
    String getOwner();

    /**
     * @return 权限所属组
     */
    @NotBlank
    String getGroup();

    /**
     * @return 资源模块名称
     */
    @NotBlank
    String getModuleName();

    /**
     * @return 资源类型
     */
    @NotNull
    <E extends DescriptiveEnum> E getResourceType();

    /**
     * @return 资源唯一标识
     */
    @NotBlank
    String getResourceId();

    /**
     * @return 资源定义
     */
    @NotBlank
    String getResourceDefinition();

    /**
     * @return 资源元数据
     */
    @NotNull
    default Map<String, Object> getMetadata() {
        return Collections.emptyMap();
    }
}