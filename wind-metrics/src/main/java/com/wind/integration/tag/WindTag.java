package com.wind.integration.tag;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签
 *
 * @author wuxp
 * @date 2025-06-24 10:31
 **/
public interface WindTag {

    /**
     * @return 标签名称
     */
    @NotBlank
    String getName();

    /**
     * @return 标签值
     */
    @NotBlank
    String getValue();

    static WindTag of(String name, String value) {
        return new ImmutableTag(name, value);
    }

    static List<WindTag> tags(String... keyValues) {
        WindTag[] result = new WindTag[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            result[i / 2] = WindTag.of(keyValues[i], keyValues[i + 1]);
        }
        return Arrays.asList(result);
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
            Set<String> values = result.computeIfAbsent(tag.getName(), k -> new HashSet<>());
            values.add(tag.getValue());
        }
        return result;
    }

    /**
     * 获取一组标签的标签值
     *
     * @param tags 标签
     * @return 标签值列表
     */
    @NotEmpty
    static Set<String> getTagValues(Collection<? extends WindTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptySet();
        }
        return tags.stream().map(WindTag::getValue).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    @AllArgsConstructor
    @Getter
    class ImmutableTag implements WindTag {

        private final String name;

        private final String value;
    }
}
