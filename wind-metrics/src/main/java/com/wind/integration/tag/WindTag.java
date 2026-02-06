package com.wind.integration.tag;

import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签
 *
 * @author wuxp
 * @date 2025-06-24 10:31
 **/
public interface WindTag extends Serializable {

    /**
     * @return 标签名称
     */
    @NonNull
    @Deprecated(forRemoval = true)
    String getName();

    /**
     * @return 标签值
     */
    @NonNull
    @Deprecated(forRemoval = true)
    String getValue();

    /**
     * @return 标签名称
     */
    @NonNull
    default String name() {
        return getName();
    }

    /**
     * @return 标签值
     */
    @NonNull
    default String value() {
        return getValue();
    }

    /**
     * 创建标签
     *
     * @param name  标签名称
     * @param value 标签值
     * @return 标签
     */
    static WindTag of(@NonNull String name, @NonNull String value) {
        return new ImmutableTag(name, value);
    }

    /**
     * 创建标签列表
     *
     * @param keyValues 标签键值对，偶数位表示 key, 奇数位表示 value，例如：{"信用评级","A"}
     * @return 标签列表
     */
    @NonNull
    static List<WindTag> tags(String... keyValues) {
        WindTag[] result = new WindTag[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            result[i / 2] = WindTag.of(keyValues[i], keyValues[i + 1]);
        }
        return List.of(result);
    }

    /**
     * 按照标签名称分组
     *
     * @param tags 标签列表
     * @return {
     * key: 标签名称
     * value: 标签值
     * }
     */
    @NotNull
    static Map<String, Set<String>> groupTagsKeyValues(Collection<? extends WindTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (WindTag tag : tags) {
            Set<String> values = result.computeIfAbsent(tag.name(), k -> new HashSet<>());
            values.add(tag.value());
        }
        return result;
    }

    /**
     * 获取一组标签的标签值
     *
     * @param tags 标签
     * @return 标签值列表
     */
    @NonNull
    static Set<String> getTagValues(Collection<? extends WindTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptySet();
        }
        return tags.stream().map(WindTag::value).collect(Collectors.toSet());
    }

}

