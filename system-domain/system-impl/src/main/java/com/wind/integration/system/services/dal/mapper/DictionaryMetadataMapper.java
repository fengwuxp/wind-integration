package com.wind.integration.system.services.dal.mapper;

import com.mybatisflex.core.BaseMapper;
import com.wind.integration.system.services.dal.entities.DictionaryMetadata;
import org.apache.ibatis.annotations.Mapper;


/**
 * 数据字典 Mapper
 *
 * @author wuxp
 * @since 2024-12-19
 **/
@Mapper
public interface DictionaryMetadataMapper extends BaseMapper<DictionaryMetadata> {
}