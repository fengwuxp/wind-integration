package com.wind.integration.tag;


import com.wind.integration.core.model.IdObject;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 标签服务，对于同一类数据标签名称是唯一的
 * 例如：用户对象有且仅有一个名为 "信用评价" 的标签，一个标签名称有且只有一个值
 * 如果某个标签有多个来源，则取优先级最高的标签值，例如
 * {@link TagSource#MANUAL}    信用评级 = A
 * </br>
 * {@link TagSource#RISK_RULE} 信用评级 = B
 * </br>
 * 由于人工标签优先级最高，则最终结果为 "信用评级 = A"，也就是 {@link TagSource#MANUAL} 的标签会覆盖 {@link TagSource#RISK_RULE} 的标签
 *
 * @param <I> 业务对象标识类型
 * @author wuxp
 * @date 2026-02-06 09:33
 **/
public interface EntityTagRepository<I extends Serializable> {

    /**
     * 保证对象标签
     *
     * @param target 业务对象
     * @param tag    标签
     */
    default void saveTag(@NonNull IdObject<I> target, @NonNull EntityTag tag) {
        saveTags(target, List.of(tag));
    }

    /**
     * 保存对象标签，如果标签已存在则更新标签值
     * 仅当新标签来源优先级 >= 当前优先级时会进行覆盖，否则忽略写入
     *
     * @param target 业务对象
     * @param tags   标签列表
     */
    void saveTags(@NonNull IdObject<I> target, @NonNull Collection<EntityTag> tags);

    /**
     * 获取对象指定名称的标签
     *
     * @param objectId 数据标识
     * @param tagName  标签名称
     * @return 标签
     */
    @Nullable
    EntityTag getTag(@NotNull I objectId, @NonNull String tagName);

    /**
     * 获取对象所有标签
     *
     * @param objectId 数据标识
     * @return 标签
     */
    @NonNull
    List<EntityTag> getTags(@NonNull I objectId);

    /**
     * 获取对象指定来源标签列表
     *
     * @param objectId 数据标识
     * @param source   标签来源
     * @return 标签
     */
    @NonNull
    List<EntityTag> getTags(@NonNull I objectId, @NonNull TagSource source);

    /**
     * 删除对象指定名称的标签
     *
     * @param objectId 数据标识
     * @param tagName  标签名称
     */
    void deleteTag(@NonNull I objectId, @NonNull String tagName);

    /**
     * 删除对象下所有标签
     *
     * @param objectId 数据标识
     */
    void deleteTags(@NonNull I objectId);

    /**
     * 删除对象下制定来源的标签
     *
     * @param objectId 数据标识
     * @param source   标签来源
     */
    void deleteTags(@NonNull I objectId, @NonNull TagSource source);

}
