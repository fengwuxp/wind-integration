package com.wind.mybatis.flex;

import com.mybatisflex.core.dialect.DialectFactory;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author wuxp
 * @date 2025-06-04 18:02
 **/
class MybatisQueryMethodHelperTests {

    private final String tableName = "t_example";

    @Test
    void testMatchFullText() {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(tableName)
                .and(MybatisQueryMethodHelper.matchFullText(new QueryColumn(tableName, "desc"), "zhans"));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM `t_example` WHERE  MATCH(`t_example`.`desc`) AGAINST ('zhans' IN NATURAL LANGUAGE MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithBoolean() {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(tableName)
                .and(MybatisQueryMethodHelper.matchFullTextWithBoolean(new QueryColumn(tableName, "desc"), "zhans"));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM `t_example` WHERE  MATCH(`t_example`.`desc`) AGAINST ('zhans' IN BOOLEAN MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithBooleanDisableOperations() {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(tableName)
                .and(MybatisQueryMethodHelper.matchFullTextWithBoolean(new QueryColumn(tableName, "desc"), "zhans*"));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM `t_example` WHERE  MATCH(`t_example`.`desc`) AGAINST ('\"zhans*\"' IN BOOLEAN MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithBooleanOperations() {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(tableName)
                .and(MybatisQueryMethodHelper.matchFullTextWithBoolean(new QueryColumn(tableName, "desc"), "zhans*", true));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM `t_example` WHERE  MATCH(`t_example`.`desc`) AGAINST ('zhans*' IN BOOLEAN MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithEmpty() {
        QueryWrapper wrapper = QueryWrapper.create()
                .from(tableName)
                .and(MybatisQueryMethodHelper.matchFullText(new QueryColumn(tableName, "desc"), ""));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM `t_example`", sql);
    }

    @Test
    @SuppressWarnings("removal")
    void testFindInSetCollectionKeepsLegacyBinaryContract() throws Exception {
        Method method = MybatisQueryMethodHelper.class.getMethod("findInSet", QueryColumn.class, Set.class);
        Assertions.assertEquals(String.class, method.getReturnType());

        Object condition = MybatisQueryMethodHelper.findInSet(
                new QueryColumn(tableName, "identity"),
                new LinkedHashSet<>(List.of("OPENAPI", "MEMBER")));
        Assertions.assertEquals(
                "( FIND_IN_SET ('OPENAPI', `t_example`.`identity`) > 0 OR FIND_IN_SET ('MEMBER', `t_example`.`identity`) > 0 )",
                condition);
    }

    @Test
    @SuppressWarnings("removal")
    void testFindInSetCollectionRejectsUnsupportedCharacters() {
        String maliciousText = "MEMBER') OR 1 = 1 --";
        Assertions.assertThrows(IllegalArgumentException.class, () -> MybatisQueryMethodHelper.findInSet(
                new QueryColumn(tableName, "identity"),
                new LinkedHashSet<>(List.of("OPENAPI", maliciousText))));
    }

    @Test
    void testFindInSetConditionUsesParameters() {
        String maliciousText = "MEMBER') OR 1 = 1 --";
        QueryWrapper wrapper = QueryWrapper.create()
                .from(tableName)
                .and(MybatisQueryMethodHelper.findInSetCondition(
                        new QueryColumn(tableName, "identity"),
                        new LinkedHashSet<>(List.of("OPENAPI", maliciousText))));

        String preparedSql = DialectFactory.getDialect().forSelectByQuery(wrapper);
        Assertions.assertFalse(preparedSql.contains(maliciousText));
        Assertions.assertEquals(2, preparedSql.chars().filter(character -> character == '?').count());
    }
}
