package com.wind.integration.system.services;

import com.wind.common.query.supports.Pagination;
import com.wind.integration.system.model.dto.DictionaryMetadataDTO;
import com.wind.integration.system.model.query.DictionaryMetadataQuery;
import com.wind.integration.system.model.rquest.SaveDictionaryMetadataRequest;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 数据字典服务
 *
 * @author wuxp
 * @since 2024-12-19
 */
public interface DictionaryMetadataService {

    /**
     * 保存 数据字典
     *
     * @param req 创建请求对象
     * @return 数据字典 ID
     */
    Long saveDictionaryMetadata(SaveDictionaryMetadataRequest req);

    /**
     * 删除数据字典
     *
     * @param id 数据字典 id
     */
    default void deleteDictionaryMetadataById(@NotNull Long id) {
        deleteDictionaryMetadataByIds(id);
    }

    /**
     * 批量删除数据字典
     *
     * @param ids 数据字典 id
     */
    void deleteDictionaryMetadataByIds(@NotEmpty Long... ids);

    /**
     * 根据 id 查询数据字典
     *
     * @param id 数据字典 id
     * @return DictionaryMetadata
     */
    DictionaryMetadataDTO queryDictionaryMetadataById(@NotNull Long id);

    /**
     * 分页查询 数据字典
     *
     * @param query 查询条件
     * @return DictionaryMetadata 分页对象
     */
    Pagination<DictionaryMetadataDTO> queryDictionaryMetadatas(DictionaryMetadataQuery query);

}