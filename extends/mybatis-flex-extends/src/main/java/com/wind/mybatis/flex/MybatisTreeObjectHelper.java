package com.wind.mybatis.flex;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.common.util.WindReflectUtils;
import com.wind.integration.core.resources.TreeResources;
import com.wind.integration.core.resources.WindResources;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于约定的树形对象 数据库查询填充 helper
 *
 * @author wuxp
 * @date 2025-12-03 15:30
 **/
public final class MybatisTreeObjectHelper {

    private MybatisTreeObjectHelper() {
        throw new AssertionError();
    }

    /**
     * 树形对象父级 id
     */
    private static final QueryColumn PARENT_ID = new QueryColumn("parent_id");

    /**
     * 填充树形对象子级
     *
     * @param elements 树形对象列表
     * @param mapper   树形对象 mapper
     */
    public static void fillChildren(@NotNull List<? extends TreeResources<? extends Serializable>> elements,
                                    BaseMapper<? extends TreeResources<? extends Serializable>> mapper) {
        Set<Serializable> ids = elements.stream().map(TreeResources::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        QueryWrapper wrapper = QueryWrapper.create().where(PARENT_ID.in(ids));
        Map<? extends Serializable, ? extends List<TreeResources<?>>> caches = mapper.selectListByQuery(wrapper)
                .stream()
                .collect(Collectors.groupingBy(TreeResources::getParentId, Collectors.mapping(Function.identity(), Collectors.toList())));

        elements.forEach(element -> {
            List<? extends TreeResources<?>> children = caches.get(element.getId());
            WindReflectUtils.setFieldValue("children", element, children == null ? Collections.emptyList() : children);
        });
    }


    /**
     * 填充树形对象父级名称
     *
     * @param elements 树形对象列表
     * @param mapper   树形对象 mapper
     */
    public static void fillParentName(@NotNull List<? extends TreeResources<? extends Serializable>> elements,
                                      @NotNull BaseMapper<? extends WindResources<? extends Serializable>> mapper) {
        Set<Serializable> parentIds = elements.stream().map(TreeResources::getParentId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return;
        }
        Map<Serializable, String> parentNames = mapper.selectListByIds(parentIds).stream().collect(Collectors.toMap(WindResources::getId, WindResources::getName));
        elements.forEach(element -> {
            if (element.getParentId() == null) {
                return;
            }
            WindReflectUtils.setFieldValue("parentName", element, parentNames.get(element.getParentId()));
        });
    }
}
