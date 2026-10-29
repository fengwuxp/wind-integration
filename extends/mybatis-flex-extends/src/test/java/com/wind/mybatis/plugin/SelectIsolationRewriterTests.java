package com.wind.mybatis.plugin;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.BaseMapper;
import com.wind.integration.core.model.TenantIsolationObject;
import com.wind.mybatis.isolation.DataIsolationScope;
import com.wind.mybatis.plugin.DataIsolationQueryInterceptor.Rule;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.wind.integration.core.enums.DataIsolationDimensions.TENANT;

class SelectIsolationRewriterTests {

    @Test
    void testRepeatedPublicEntityQuerySkipsSqlParsing() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String sql = "SELECT id FROM public_records WHERE id = ?";
        MappedStatement statement = statement(configuration, sql);
        BoundSql boundSql = statement.getBoundSql(1L);

        Assertions.assertSame(boundSql, rewriter.rewrite(statement, boundSql));
        Assertions.assertSame(boundSql, rewriter.rewrite(statement, boundSql));
    }

    @Test
    void testChangedSqlOnSameStatementStillChecksProtectedTable() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        MappedStatement statement = statement(configuration, "SELECT id FROM public_records");
        BoundSql publicSql = statement.getBoundSql(null);
        Assertions.assertSame(publicSql, rewriter.rewrite(statement, publicSql));

        BoundSql protectedSql = new BoundSql(configuration, "SELECT id FROM protected_records", List.of(), null);
        BoundSql isolated = rewriter.rewrite(statement, protectedSql);
        Assertions.assertNotSame(protectedSql, isolated);
        Assertions.assertTrue(isolated.getSql().contains("tenant_id"));

        BoundSql joinedSql = new BoundSql(configuration,
                "SELECT p.id FROM public_records p JOIN protected_records r ON p.id = r.id", List.of(), null);
        Assertions.assertThrows(IllegalStateException.class, () -> rewriter.rewrite(statement, joinedSql));
    }

    @Test
    void testUnregisteredOrComplexQueriesDoNotEnterFastPath() {
        Configuration configuration = configuration();
        SelectIsolationRewriter rewriter = rewriter(configuration);
        String unknownSql = "SELECT id FROM another_public_table";
        String complexSql = "SELECT p.id FROM public_records p JOIN public_records q ON p.id = q.id";
        MappedStatement statement = statement(configuration, unknownSql);

        BoundSql unknown = statement.getBoundSql(null);
        Assertions.assertSame(unknown, rewriter.rewrite(statement, unknown));
        Assertions.assertSame(unknown, rewriter.rewrite(statement, unknown));
        BoundSql complex = new BoundSql(configuration, complexSql, List.of(), null);
        Assertions.assertSame(complex, rewriter.rewrite(statement, complex));
        Assertions.assertSame(complex, rewriter.rewrite(statement, complex));
    }

    private Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.addMapper(PublicMapper.class);
        configuration.addMapper(ProtectedMapper.class);
        return configuration;
    }

    private SelectIsolationRewriter rewriter(Configuration configuration) {
        return new SelectIsolationRewriter(configuration,
                List.of(new Rule(TENANT, "tenantId", () -> DataIsolationScope.of(10L))));
    }

    private MappedStatement statement(Configuration configuration, String sql) {
        return new MappedStatement.Builder(configuration, "test.query", new StaticSqlSource(configuration, sql),
                SqlCommandType.SELECT).build();
    }

    @Table("public_records")
    static class PublicRecord {
        @Id
        private Long id;
    }

    @Table("protected_records")
    static class ProtectedRecord implements TenantIsolationObject<Long> {
        @Id
        private Long id;
        private Long tenantId;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public Long getTenantId() {
            return tenantId;
        }
    }

    interface PublicMapper extends BaseMapper<PublicRecord> {
    }

    interface ProtectedMapper extends BaseMapper<ProtectedRecord> {
    }
}
