package com.wind.integration.system.services.dal.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wind.integration.system.services.dal.entities.SystemConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置 Mapper
 *
 * @author wuxp
 * @since 2024-12-19
 **/
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {
}