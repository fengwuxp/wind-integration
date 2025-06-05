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


    @Test
    void testMatchFullText() {
        QueryWrapper wrapper = QueryWrapper.create()
                .and(MybatisQueryMethodHelper.matchFullText(new QueryColumn("t_example", "desc"), "zhans"));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM  WHERE  MATCH(`t_example`.`desc`) AGAINST ('zhans' IN NATURAL LANGUAGE MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithBoolean() {
        QueryWrapper wrapper = QueryWrapper.create()
                .and(MybatisQueryMethodHelper.matchFullTextWithBoolean(new QueryColumn("t_example", "desc"), "zhans"));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM  WHERE  MATCH(`t_example`.`desc`) AGAINST ('zhans' IN BOOLEAN MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithBooleanDisableOperations() {
        QueryWrapper wrapper = QueryWrapper.create()
                .and(MybatisQueryMethodHelper.matchFullTextWithBoolean(new QueryColumn("t_example", "desc"), "zhans*"));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM  WHERE  MATCH(`t_example`.`desc`) AGAINST ('\"zhans*\"' IN BOOLEAN MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithBooleanOperations() {
        QueryWrapper wrapper = QueryWrapper.create()
                .and(MybatisQueryMethodHelper.matchFullTextWithBoolean(new QueryColumn("t_example", "desc"), "zhans*", true));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM  WHERE  MATCH(`t_example`.`desc`) AGAINST ('zhans*' IN BOOLEAN MODE) ", sql);
    }

    @Test
    void testMatchFullTextWithEmpty() {
        QueryWrapper wrapper = QueryWrapper.create()
                .and(MybatisQueryMethodHelper.matchFullText(new QueryColumn("t_example", "desc"), ""));
        String sql = wrapper.toSQL();
        Assertions.assertEquals("SELECT * FROM ", sql);
    }
}
