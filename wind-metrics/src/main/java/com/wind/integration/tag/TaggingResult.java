package com.wind.integration.tag;

import com.wind.integration.core.model.IdObject;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

/**
 * 打标结果
 *
 * @param data 打标数据
 * @param tags 打标结果
 * @author wuxp
 * @date 2026-02-06 10:33
 */
public record TaggingResult(@NonNull IdObject<?> data, @NonNull Collection<EntityTag> tags) {

}
