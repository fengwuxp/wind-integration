package com.wind.mybatis.flex;


import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryOrderBy;
import com.mybatisflex.core.query.QueryWrapper;
import com.wind.common.query.WindQuery;
import com.wind.common.query.cursor.AbstractCursorQuery;
import com.wind.common.query.cursor.CursorPagination;
import com.wind.common.query.supports.AbstractPageQuery;
import com.wind.common.query.supports.Pagination;
import com.wind.common.query.supports.QueryOrderField;
import com.wind.common.query.supports.QueryOrderType;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

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

    private static final QueryColumn CURSOR_ID_COLUMN = new QueryColumn("id");

    private MybatisQueryHelper() {
        throw new AssertionError();
    }

    /**
     * TODO 统一使用 WindQuery ？
     * 通过查询请求创建一个 mybatis flex 分页对象
     *
     * @param query 查询请求
     * @return mybatis flex 分页对象
     */
    @NotNull
    public static <T> Page<T> of(AbstractPageQuery<?> query) {
        Integer querySize = Math.min(query.getQuerySize(), query.getMaxQuerySize());
        return new Page<>(query.getQueryPage(), querySize);
    }

    @NotNull
    public static QueryWrapper from(WindQuery<? extends QueryOrderField> query) {
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
    @NotNull
    public static QueryWrapper from(WindQuery<? extends QueryOrderField> query, String tableAlias) {
        if (query.shouldOrderBy()) {
            QueryWrapper result = QueryWrapper.create();
            for (int i = 0; i < query.getOrderFields().length; i++) {
                String orderField = query.getOrderFields()[i].getOrderField();
                if (tableAlias != null) {
                    orderField = String.format("%s.%s", tableAlias, orderField);
                }
                result.orderBy(orderField, Objects.equals(Objects.requireNonNull(query.getOrderTypes())[i], QueryOrderType.ASC));
            }
            if (query instanceof AbstractCursorQuery<? extends QueryOrderField> cursorQuery) {
                // 如果是游标查询，自动设置游标条件、分页大小
                result.and(MybatisQueryHelper.cursorConditionWithNumId(CURSOR_ID_COLUMN, cursorQuery))
                        .limit(query.getQuerySize());
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
     * 获取基于数值型游标字段的查询条件（适用于自增ID、时间戳等数值类型）
     *
     * <p>说明：
     * <ul>
     *   <li>游标分页的核心是“稳定排序 + 唯一游标字段”，例如 {@code ORDER BY created_time DESC, id DESC}。</li>
     *   <li>若查询上一页（存在 prevCursor），则比较符号要与排序方向相反。</li>
     *   <li>若查询下一页（不存在 prevCursor，仅 nextCursor），则比较符号要与排序方向保持一致。</li>
     * </ul>
     *
     * <p>示例（ASC 排序）：
     * <pre>{@code
     *   WHERE id > :cursor ORDER BY id ASC LIMIT 20
     * }</pre>
     * <p>示例（DESC 排序）：
     * <pre>{@code
     *   WHERE id < :cursor ORDER BY id DESC LIMIT 20
     * }</pre>
     */
    @NotNull
    public static QueryCondition cursorConditionWithNumId(QueryColumn cursorColumn, AbstractCursorQuery<?> query) {
        return cursorConditionWithId(cursorColumn, query, query::asPrevNumberId, query::asNextNumberId);
    }

    /**
     * 获取基于字符串型游标字段的查询条件（适用于 UUID、雪花ID、复合编码等文本类型）
     *
     * <p>说明：
     * <ul>
     *   <li>字符串比较使用字典序（lexicographical order）。例如："10" < "2"。</li>
     *   <li>若字符串ID可转为数值且保证等长（如雪花ID），则字典序排序与数值排序一致。</li>
     *   <li>其余逻辑与数值型一致，上一页与下一页通过 < / > 控制方向。</li>
     * </ul>
     */
    @NotNull
    public static QueryCondition cursorConditionWithTextId(QueryColumn cursorColumn, AbstractCursorQuery<?> query) {
        return cursorConditionWithId(cursorColumn, query, query::asPrevTextId, query::asNextTextId);
    }

    /**
     * 下一页
     * 正序： id > : lastCursor      {1...10,11...20,21...30}
     * 倒序： id < : lastCursor      {99...90,89...80,79...70}
     * 上一页：
     * 正序: id < : firstCursor       {1...10,11...20,21...30}
     * 倒序: id > : firstCursor       {99...90,89...80,79...70}
     */
    private static QueryCondition cursorConditionWithId(QueryColumn cursorColumn, AbstractCursorQuery<?> query, Supplier<Object> prevIdGetter, Supplier<Object> nextIdGetter) {
        boolean asc = query.cursorFieldIsAcs();
        // 前后翻页的排序方式相反，此处只需要按照翻页方向获取游标 id 即可
        Object cursorId = query.getPrevCursor() == null ? nextIdGetter.get() : prevIdGetter.get();
        return asc ? cursorColumn.gt(cursorId) : cursorColumn.lt(cursorId);
    }

    /**
     * 将 mybatis plus 的分页对象转换为统一分页对象
     *
     * @param data      mybatis plus 分页数据
     * @param query     查询请求
     * @param converter 转换实体为 DOT 的函数
     * @return 统一分页对象
     */
    @NotNull
    public static <T, R> Pagination<R> convert(Page<T> data, AbstractPageQuery<?> query, Function<T, R> converter) {
        List<R> records = data.getRecords().stream().map(converter).toList();
        return Pagination.of(records, query, data.getTotalRow());
    }

    /**
     * 将 mybatis plus 的分页对象转换为统一分页对象
     *
     * @param data    mybatis plus 分页数据
     * @param query   查询请求
     * @param records DTO 列表
     * @return 统一分页对象
     */
    @NotNull
    public static <T, R> Pagination<R> convert(Page<T> data, AbstractPageQuery<?> query, List<R> records) {
        return Pagination.of(records, query, data.getTotalRow());
    }

    /**
     * 将 mybatis plus 的分页对象转换为统一分页对象
     *
     * @param data      查询结果数据
     * @param query     游标查询请求
     * @param converter 转换实体为 DOT 的函数
     * @return 统一分页对象
     */
    @NotNull
    public static <T, R> CursorPagination<R> convert(List<T> data, AbstractCursorQuery<?> query, Function<T, R> converter) {
        List<R> records = data.stream().map(converter).toList();
        return CursorPagination.of(records, query);
    }

}
