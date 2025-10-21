package com.wind.integration.tag;

import com.wind.common.exception.AssertUtils;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

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
    String name();

    /**
     * @return 标签值
     */
    @NotBlank
    String value();

    static WindTag of(@NotBlank String name, @NotNull String value) {
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
    @NotEmpty
    static Set<String> getTagValues(Collection<? extends WindTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptySet();
        }
        return tags.stream().map(WindTag::value).filter(Objects::nonNull).collect(Collectors.toSet());
    }


    record ImmutableTag(String name, String value) implements WindTag {
        public ImmutableTag {
            AssertUtils.hasText(name, "argument name must not empty");
            AssertUtils.notNull(value, "argument value must not null");
        }
    }
}
