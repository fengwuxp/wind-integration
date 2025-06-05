package com.wind.mybatis.flex;

import com.google.common.collect.ImmutableSet;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.RawQueryCondition;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotEmpty;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySql 数据库函数支持
 *
 * @author wuxp
 * @date 2023-11-20 12:54
 **/
public final class MybatisQueryMethodHelper {

    /**
     * 全文索引布尔模式中的特殊操作符，必须处理
     */
    private static final Set<Character> BOOLEAN_MODE_OPERATORS = ImmutableSet.of(
            '+', '-', '*', '>', '<', '~', '"', '(', ')', '@'
    );


    private MybatisQueryMethodHelper() {
        throw new AssertionError();
    }

    /**
     * 参见 https://www.yiibai.com/mysql/find_in_set.html
     * 在 {@param column} 字段中
     *
     * @param column 查询的字段
     * @param text   查找的文本
     * @return QueryCondition
     */
    public static QueryCondition findInSet(QueryColumn column, @Nullable String text) {
        return new RawQueryCondition(String.format("FIND_IN_SET (?, %s)", buildField(column)), text);
    }

    /**
     * 不在 {@param column} 字段中
     *
     * @param column 查询的字段
     * @param text   查找的文本
     * @return QueryCondition
     */
    public static QueryCondition notFindInSet(QueryColumn column, @Nullable String text) {
        return new RawQueryCondition(String.format("NOT FIND_IN_SET (?, %s)", buildField(column)), text);
    }

    public static QueryCondition findInSet(QueryColumn column, @Nullable Enum<?> content) {
        if (content == null) {
            return QueryCondition.createEmpty();
        }
        return findInSet(column, content.name());
    }

    public static QueryCondition notFindInSet(QueryColumn column, @Nullable Enum<?> content) {
        if (content == null) {
            return QueryCondition.createEmpty();
        }
        return notFindInSet(column, content.name());
    }

    /**
     * FIND_IN_SET 字段值在集合中
     *
     * @param column 查询的字段
     * @param texts  查找的文本
     *               使用示例:
     *               <p>
     *               <code>
     *               column:
     *               t_user.identity
     *               texts:
     *               Set<String> identitys = new HashSet<>();
     *               identitys.add("OPENAPI");
     *               identitys.add("MEMBER");
     *               生成的SQL:
     *               (FIND_IN_SET('OPENAPI', identity) > 0 OR FIND_IN_SET('MEMBER', identity) > 0)
     *               </code>
     *               <p/>
     * @return String sql
     */
    public static String findInSet(QueryColumn column, @NotEmpty Set<String> texts) {
        StringBuilder conditions = new StringBuilder();
        for (String name : texts) {
            if (conditions.length() > 0) {
                conditions.append(" OR ");
            }
            conditions.append(String.format("FIND_IN_SET ('%s', %s) > 0", name, buildField(column)));
        }
        return String.format("( %s )", conditions);
    }

    /**
     * 构建全文搜索查询（自然语言模式）
     *
     * @param column  查询字段
     * @param keyword 查询关键词
     * @return QueryCondition
     */
    public static QueryCondition matchFullText(QueryColumn column, String keyword) {
        return matchFullText(Collections.singletonList(column), keyword, false, false);
    }

    /**
     * 构建全文搜索查询（布尔模式）
     *
     * @param column  查询字段
     * @param keyword 查询关键词
     * @return QueryCondition
     */
    public static QueryCondition matchFullTextWithBoolean(QueryColumn column, String keyword) {
        return matchFullTextWithBoolean(column, keyword, false);
    }

    /**
     * 构建全文搜索查询（布尔模式）
     *
     * @param column           查询字段
     * @param keyword          查询关键词
     * @param enableOperations 开启操作符支持
     * @return QueryCondition
     */
    public static QueryCondition matchFullTextWithBoolean(QueryColumn column, String keyword, boolean enableOperations) {
        return matchFullText(Collections.singletonList(column), keyword, true, enableOperations);
    }

    /**
     * 构建全文搜索查询
     *
     * @param columns          查询字段（可多个）
     * @param keyword          查询关键词
     * @param booleanMode      是否启用布尔模式
     * @param enableOperations 布尔模式是否开启操作符支持
     * @return QueryCondition
     */
    private static QueryCondition matchFullText(@NotEmpty List<QueryColumn> columns, String keyword, boolean booleanMode, boolean enableOperations) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return QueryCondition.createEmpty();
        }

        String fields = columns.stream()
                .map(MybatisQueryMethodHelper::buildField)
                .collect(Collectors.joining(", "));
        String mode = booleanMode ? "IN BOOLEAN MODE" : "IN NATURAL LANGUAGE MODE";
        String sql = String.format("MATCH(%s) AGAINST (? %s)", fields, mode);
        return new RawQueryCondition(sql, booleanMode && !enableOperations ? escapeForBooleanMode(keyword) : keyword);
    }

    private static String buildField(QueryColumn column) {
        return String.format("`%s`.`%s`", column.getTable().getName(), column.getName());
    }

    /**
     * 自动为布尔模式内容加上双引号，并处理特殊符号（包裹为短语）
     */
    private static String escapeForBooleanMode(String keyword) {
        // 先简单过滤掉特殊控制字符（如换行、制表符等）
        String cleaned = keyword.replaceAll("[\\t\\n\\r]", " ").trim();

        // 如果包含操作符字符，则将整个关键词包裹成短语
        boolean containsSpecialChar = cleaned.chars()
                .mapToObj(c -> (char) c)
                .anyMatch(BOOLEAN_MODE_OPERATORS::contains);

        // 全部作为短语搜索，避免误判为逻辑控制符
        return containsSpecialChar ? "\"" + cleaned + "\"" : cleaned;
    }

}
