package com.wind.integration.tenant.services;

import com.wind.integration.tenant.model.request.CreateTenantRequest;

/**
 * 租户服务
 *
 * @author wuxp
 * @date 2025-06-13 11:14
 **/
public interface TenantService {

    /**
     * 创建租户
     *
     * @param request 租户创建参数
     * @return 租户id
     */
    Long createTenant(CreateTenantRequest request);
}
