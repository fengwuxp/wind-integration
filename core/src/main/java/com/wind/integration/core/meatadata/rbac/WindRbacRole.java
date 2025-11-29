package com.wind.integration.core.meatadata.rbac;

import com.wind.integration.core.resources.WindResources;
import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;
import java.util.Set;

/**
 * wind-rbac 角色定义
 *
 * @author wuxp
 * @date 2025-11-26 18:10
 **/
public interface WindRbacRole<ID extends Serializable> extends WindResources<ID> {

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
     * 获取权限ids
     *
     * @return 权限ids
     */
    Set<ID> getPermissionIds();
}
