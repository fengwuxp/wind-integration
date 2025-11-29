package com.wind.integration.core.meatadata.rbac;

import com.wind.integration.core.resources.WindResources;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Set;

/**
 * wind-rbac 权限定义
 *
 * @author wuxp
 * @date 2025-11-26 18:10
 **/
public interface WindRbacPermission<ID extends Serializable> extends WindResources<ID> {

    /**
     * @return 权限所属者
     */
    @NotBlank
    String getOwner();

    /**
     * @return 权限所属组
     */
    @NotBlank
    String getGroup();

    /**
     * @return 绑定的资源
     */
    @NotNull
    Set<ID> getResourceIds();

}
