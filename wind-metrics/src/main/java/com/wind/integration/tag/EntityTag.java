package com.wind.integration.tag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.common.util.StringJoinSplitUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务标签对象
 *
 * @param name     标签名称
 * @param value    标签值
 * @param source   标签来源
 * @param sourceId 标记标签的来源标识
 * @author wuxp
 * @date 2024-09-11 09:52
 */
public record EntityTag(@JsonProperty("name") @NonNull String name,
                        @JsonProperty("value") @NonNull String value,
                        @JsonProperty("source") @NonNull TagSource source,
                        @JsonProperty("sourceId") @NonNull Serializable sourceId) implements WindTag {


    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public EntityTag {
        AssertUtils.hasText(name, "argument name must not emtpy");
        AssertUtils.hasText(value, "argument value must not emtpy");
        AssertUtils.notNull(source, "argument source must not null");
        AssertUtils.notNull(sourceId, "argument sourceId must not null");
    }

    @Override
    public @NonNull String getName() {
        return name();
    }

    @Override
    public @NonNull String getValue() {
        return value();
    }

    /**
     * 创建标签
     *
     * @param name     标签名称
     * @param value    标签值
     * @param source   标签来源
     * @param sourceId 标签来源标识
     * @return 标签
     */
    public static EntityTag of(@NonNull String name, @NonNull String value, @NonNull TagSource source, @NonNull Serializable sourceId) {
        return new EntityTag(name, value, source, sourceId);
    }

    /**
     * 创建标签
     *
     * @param source    标签来源
     * @param sourceId  标签来源标识
     * @param keyValues 标签键值对，偶数位表示 key, 奇数位表示 value，例如：{"信用评级","A"}
     * @return 标签列表
     */
    public static List<EntityTag> tags(@NonNull TagSource source, @NonNull Serializable sourceId, @Nullable String... keyValues) {
        if (keyValues == null) {
            return Collections.emptyList();
        }
        EntityTag[] result = new EntityTag[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            result[i / 2] = EntityTag.of(keyValues[i], keyValues[i + 1], source, sourceId);
        }
        return Arrays.asList(result);
    }

    /**
     * 将标签列表转换为业务标签对象列表
     *
     * @param source   标签来源
     * @param sourceId 标签来源标识
     * @param tags     标签列表
     * @return 业务标签对象列表
     */
    public static List<EntityTag> tags(@NonNull TagSource source, @NonNull Serializable sourceId, @Nullable List<WindTag> tags) {
        if (tags == null) {
            return Collections.emptyList();
        }
        List<EntityTag> result = new ArrayList<>(tags.size());
        tags.forEach(tag -> result.add(EntityTag.of(tag.name(), tag.value(), source, sourceId)));
        return Collections.unmodifiableList(result);
    }


    /**
     * 将多个标签值合并为同一个，使用 ',' 连接多个值
     *
     * @param tageName  标签名称
     * @param tagValues 标签值
     * @return 标签
     */
    public static EntityTag mergeTagValues(String tageName, Collection<EntityTag> tagValues) {
        Set<TagSource> sources = tagValues.stream().map(EntityTag::source).collect(Collectors.toSet());
        Set<Serializable> sourceIds = tagValues.stream().map(EntityTag::sourceId).collect(Collectors.toSet());
        AssertUtils.isTrue(sources.size() == 1, () -> "标签: " + tageName + " 的标签来源不唯一, tag sources = " + StringJoinSplitUtils.joinEnums(sources));
        AssertUtils.isTrue(sourceIds.size() == 1, () -> "标签: " + tageName + " 的标签来源不唯一, tag sources id = " + StringJoinSplitUtils.join(sourceIds));
        String tagValue = tagValues.stream().map(EntityTag::value).collect(Collectors.joining(WindConstants.COMMA));
        return EntityTag.of(tageName, tagValue, CollectionUtils.firstElement(sources), CollectionUtils.firstElement(sourceIds));
    }

}
