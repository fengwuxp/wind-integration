package com.wind.integration.system.services;

import com.wind.common.query.supports.Pagination;
import com.wind.integration.system.model.dto.SystemConfigDTO;
import com.wind.integration.system.model.query.SystemConfigQuery;
import com.wind.integration.system.model.rquest.SaveSystemConfigRequest;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * 系统配置服务
 *
 * @author wuxp
 * @since 2024-12-19
 */
public interface SystemConfigService {

    /**
     * 保存 系统配置
     *
     * @param req 创建请求对象
     * @return 系统配置 ID
     */
    Long saveSystemConfig(SaveSystemConfigRequest req);

    /**
     * 删除系统配置
     *
     * @param id 系统配置 id
     */
    default void deleteSystemConfigById(@NotNull Long id) {
        deleteSystemConfigByIds(id);
    }

    /**
     * 批量删除系统配置
     *
     * @param ids 系统配置 id
     */
    void deleteSystemConfigByIds(@NotEmpty Long... ids);

    /**
     * 根据 id 查询系统配置
     *
     * @param id 系统配置 id
     * @return SystemConfig
     */
    SystemConfigDTO querySystemConfigById(@NotNull Long id);

    /**
     * 分页查询 系统配置
     *
     * @param query 查询条件
     * @return SystemConfig 分页对象
     */
    Pagination<SystemConfigDTO> querySystemConfigs(SystemConfigQuery query);

}