package com.wind.integration.tag;

import com.wind.core.ReadonlyContextVariables;
import com.wind.integration.core.model.IdObject;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * 打标器评估器
 *
 * @author wuxp
 * @date 2026-02-06 11:00
 **/
public interface EntityTagEvaluator {

    /**
     * 评估对象需要打标的标签
     *
     * @param data 对象数据
     * @return 计算结果
     */
    @NonNull
    default Collection<EntityTag> eval(@NonNull IdObject<?> data) {
        return eval(data, ReadonlyContextVariables.empty());
    }

    /**
     * 评估对象需要打标的标签
     *
     * @param data             对象数据
     * @param contextVariables 上下文变量
     * @return 计算结果
     */
    @NonNull
    Collection<EntityTag> eval(@NonNull IdObject<?> data, @NonNull ReadonlyContextVariables contextVariables);

    /**
     * 获取支持的标签来源
     *
     * @return 标签来源
     */
    @NonNull
    TagSource getSupportsSource();
}
