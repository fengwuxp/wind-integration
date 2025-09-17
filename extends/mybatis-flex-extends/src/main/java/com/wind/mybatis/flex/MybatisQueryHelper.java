package com.wind.mybatis.flex;


import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryOrderBy;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.common.query.supports.AbstractPageQuery;
import com.wind.common.query.supports.Pagination;
import com.wind.common.query.supports.QueryOrderField;
import com.wind.common.query.supports.QueryOrderType;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * mybatis flex query helper
 *
 * @author wuxp
 */
public final class MybatisQueryHelper {

    /**
     * 默认的表别名
     */
    public static final String DEFAULT_TABLE_ALIAS = "t";

    private MybatisQueryHelper() {
        throw new AssertionError();
    }

    /**
     * 通过查询请求创建一个 mybatis flex 分页对象
     *
     * @param query 查询请求
     * @return mybatis flex 分页对象
     */
    public static <T> Page<T> of(AbstractPageQuery<?> query) {
        Integer querySize = Math.min(query.getQuerySize(), query.getMaxQuerySize());
        return new Page<>(query.getQueryPage(), querySize);
    }

    public static QueryWrapper from(AbstractPageQuery<?> query) {
        return from(query, null);
    }

    /**
     * 通过 query 构建一个 QueryWrapper
     * 设置排序参数
     *
     * @param query      查询请求
     * @param tableAlias 表别名
     * @return QueryWrapper
     */
    public static QueryWrapper from(AbstractPageQuery<?> query, String tableAlias) {
        if (query.requireOrderBy()) {
            QueryWrapper result = QueryWrapper.create();
            for (int i = 0; i < query.getOrderFields().length; i++) {
                String orderField = query.getOrderFields()[i].getOrderField();
                if (tableAlias != null) {
                    orderField = String.format("%s.%s", tableAlias, orderField);
                }
                result.orderBy(orderField, Objects.equals(query.getOrderTypes()[i], QueryOrderType.ASC));
            }
            return result;
        }
        return QueryWrapper.create();
    }


    /**
     * 通过 query 获取 flex QueryOrderBy[]
     *
     * @param query 查询请求
     * @return QueryOrderBy[]
     */
    public static QueryOrderBy[] getQueryOrderBy(AbstractPageQuery<?> query) {
        return getQueryOrderBy(query.getOrderFields(), query.getOrderTypes());
    }

    public static QueryOrderBy[] getQueryOrderBy(QueryOrderField[] fields, QueryOrderType[] types) {
        if (fields == null || types == null || fields.length <= 0 || fields.length != types.length) {
            return new QueryOrderBy[0];
        }
        QueryOrderBy[] result = new QueryOrderBy[fields.length];
        for (int i = 0; i < fields.length; i++) {
            QueryOrderType orderType = Objects.equals(types[i], QueryOrderType.ASC) ? QueryOrderType.ASC : QueryOrderType.DESC;
            result[i] = new QueryOrderBy(new QueryColumn(fields[i].getOrderField()), orderType.name());
        }
        return result;
    }


    /**
     * 将 mybatis plus 的分页对象转换为统一分页对象
     *
     * @param data      mybatis plus 分页数据
     * @param req       查询请求
     * @param converter 转换实体为 DOT 的函数
     * @return 统一分页对象
     */
    public static <T, R> Pagination<R> convert(Page<T> data, AbstractPageQuery<?> req, Function<T, R> converter) {
        List<R> records = data.getRecords().stream().map(converter).toList();
        return Pagination.of(records, req, data.getTotalRow());
    }

    /**
     * 将 mybatis plus 的分页对象转换为统一分页对象
     *
     * @param data    mybatis plus 分页数据
     * @param req     查询请求
     * @param records DTO 列表
     * @return 统一分页对象
     */
    public static <T, R> Pagination<R> convert(Page<T> data, AbstractPageQuery<?> req, List<R> records) {
        return Pagination.of(records, req, data.getTotalRow());
    }
}
