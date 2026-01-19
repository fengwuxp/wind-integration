package com.wind.mybatis.flex;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.common.util.WindReflectUtils;
import com.wind.core.resources.TreeResources;
import com.wind.core.resources.WindResources;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.Serializable;
import java.util.Collection;
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
     * 构建树形对象
     *
     * @param roots      树形对象根节点
     * @param mapper    树形对象 mapper
     * @param converter 转换器
     */
    public static <E extends WindResources<? extends Serializable>> void buildTree(@NotNull List<? extends TreeResources<? extends Serializable>> roots,
                                                                                   @NonNull BaseMapper<E> mapper,
                                                                                   @NonNull Function<E, ? extends TreeResources<? extends Serializable>> converter) {
        fillChildren(roots, mapper, converter);
        List<? extends TreeResources<? extends Serializable>> current = roots;
        while (true) {
            List<? extends TreeResources<? extends Serializable>> children = current.stream()
                    .map(TreeResources::getChildren)
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .toList();
            if (children.isEmpty()) {
                return;
            }
            fillChildren(children, mapper, converter);
            current = children;
        }
    }

    /**
     * 填充树形对象子级
     *
     * @param elements 树形对象列表
     * @param mapper   树形对象 mapper
     */
    public static <E extends WindResources<? extends Serializable>> void fillChildren(@NotNull List<? extends TreeResources<? extends Serializable>> elements,
                                                                                      @NonNull BaseMapper<E> mapper,
                                                                                      @NonNull Function<E, ? extends TreeResources<? extends Serializable>> converter) {
        Set<Serializable> ids = elements.stream().map(TreeResources::getId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        QueryWrapper wrapper = QueryWrapper.create().where(PARENT_ID.in(ids));
        Map<? extends Serializable, ? extends List<TreeResources<?>>> caches = mapper.selectListByQuery(wrapper)
                .stream()
                .map(converter)
                .collect(Collectors.groupingBy(TreeResources::getParentId, Collectors.mapping(Function.identity(), Collectors.toList())));

        Map<Serializable, String> parentNames = elements.stream().collect(Collectors.toMap(WindResources::getId, WindResources::getName));
        elements.forEach(element -> {
            List<? extends TreeResources<?>> values = caches.get(element.getId());
            List<? extends TreeResources<?>> children = values == null ? Collections.emptyList() : values;
            fillParentName(parentNames, children);
            WindReflectUtils.setFieldValue("children", element, children);
        });
    }


    /**
     * 填充树形对象父级名称
     *
     * @param children 子元素列表
     * @param mapper   树形对象 mapper
     */
    public static void fillParentName(@NotNull List<? extends TreeResources<? extends Serializable>> children,
                                      @NonNull BaseMapper<? extends WindResources<? extends Serializable>> mapper) {
        Set<Serializable> parentIds = children.stream().map(TreeResources::getParentId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return;
        }
        Map<Serializable, String> parentNames = mapper.selectListByIds(parentIds).stream().collect(Collectors.toMap(WindResources::getId, WindResources::getName));
        fillParentName(parentNames, children);
    }

    private static void fillParentName(Map<Serializable, String> parentNames, List<? extends TreeResources<? extends Serializable>> children) {
        Set<Serializable> parentIds = children.stream().map(TreeResources::getParentId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (parentIds.isEmpty()) {
            return;
        }
        children.forEach(element -> {
            if (element.getParentId() == null) {
                return;
            }
            WindReflectUtils.setFieldValue("parentName", element, parentNames.get(element.getParentId()));
        });
    }
}
