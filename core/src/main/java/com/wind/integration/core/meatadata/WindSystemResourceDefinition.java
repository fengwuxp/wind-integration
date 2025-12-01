package com.wind.integration.core.meatadata;

import com.wind.common.WindConstants;
import com.wind.integration.core.resources.TreeResources;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/**
 * wind-system 资源定义
 *
 * @param <ID> 资源ID
 * @author wuxp
 * @date 2025-11-26 17:27
 **/
public interface WindSystemResourceDefinition<ID extends Serializable> extends TreeResources<ID> {

    /**
     * 例如：应用的 appName
     *
     * @return 资源所属者
     */
    @NotBlank
    String getOwner();

    /**
     * @return 资源分组
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
    String getResourceType();

    /**
     * @return 资源唯一标识
     */
    @NotBlank
    String getResourceKey();

    /**
     * @return 资源定义
     */
    @NotBlank
    String getResourceDefinition();

    /**
     * 对于 UI 资源，可以指定  UI 套系/版本
     * 例如：admin, merchant, mobile, light, dark
     */
    @NotBlank
    default String getUiVariant() {
        return WindConstants.DEFAULT_TEXT;
    }

    /**
     * @return 资源元数据
     */
    @NotNull
    default Map<String, Object> getMetadata() {
        return Collections.emptyMap();
    }
}