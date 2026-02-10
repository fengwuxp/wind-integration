package com.wind.integration.tag;

import com.wind.core.ReadonlyContextVariables;
import com.wind.integration.core.model.IdObject;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * 对象打标器
 *
 * @author wuxp
 * @date 2026-02-06 11:11
 **/
public interface EntityTagger {

    /**
     * 打标 (规则、风控打标)
     *
     * @param data 数据
     * @return 打标结果
     */
    @NonNull
    TaggingResult tag(@NonNull IdObject<?> data, @NonNull TagSource source);

    /**
     * 打标 (规则、风控打标)
     *
     * @param data      数据
     * @param source    标签来源
     * @param variables 上下文变量
     * @return 打标结果
     */
    @NonNull
    TaggingResult tag(@NonNull IdObject<?> data, @NonNull TagSource source, @NonNull ReadonlyContextVariables variables);

    /**
     * 模拟打标（仅计算）
     *
     * @param data   业务对象
     * @param source 标签来源
     * @return 标签集合
     */
    @NonNull
    default Collection<EntityTag> simulate(@NonNull IdObject<?> data, @NonNull TagSource source) {
        return simulate(data, source, ReadonlyContextVariables.empty());
    }

    /**
     * 模拟打标（仅计算）
     *
     * @param data      数据
     * @param source    标签来源
     * @param variables 上下文变量
     * @return 标签集合
     */
    @NonNull
    Collection<EntityTag> simulate(@NonNull IdObject<?> data, @NonNull TagSource source, @NonNull ReadonlyContextVariables variables);

    /**
     * 指定标签打标 （人工、风控打标）
     *
     * @param data 数据
     * @param tags 指定的标签
     * @return 打标结果
     */
    @NonNull
    Collection<EntityTag> tag(@NonNull IdObject<?> data, @NonNull Collection<EntityTag> tags);

}
