package com.wind.mybatis.flex;

import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
}
