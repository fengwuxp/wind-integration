package com.wind.integration.infrastructure.dal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProgrammaticTransactionTemplateTests {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private ProgrammaticTransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        transactions = new ProgrammaticTransactionTemplate(new DataSourceTransactionManager(database));
        jdbc.execute("CREATE TABLE orders (id INT PRIMARY KEY)");
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void testSupplierReturnsResultAfterCommit() {
        int updated = transactions.execute(() -> jdbc.update("INSERT INTO orders VALUES (1)"));

        assertEquals(1, updated);
        assertEquals(List.of(1), orderIds());
    }

    @Test
    void testFallbackReadsCommittedStateAfterRollback() {
        List<Integer> result = transactions.execute(() -> {
            jdbc.update("INSERT INTO orders VALUES (1)");
            throw new IllegalStateException("撤销订单写入");
        }, this::orderIds);

        assertEquals(List.of(), result);
        assertEquals(List.of(), orderIds());
    }

    /**
     * 外层失败时，默认传播的写入随之回滚，独立事务已提交的订单仍可查询。
     */
    @Test
    void testOuterRollbackWithRequiredAndRequiresNew() {
        IllegalStateException failure = new IllegalStateException("撤销外层事务");
        Runnable outer = () -> {
            transactions.execute(() -> jdbc.update("INSERT INTO orders VALUES (1)"));
            transactions.execute(() -> jdbc.update("INSERT INTO orders VALUES (2)"), Propagation.REQUIRES_NEW);
            throw failure;
        };

        assertSame(failure, assertThrows(IllegalStateException.class, () -> transactions.execute(outer)));
        assertEquals(List.of(2), orderIds());
    }

    private List<Integer> orderIds() {
        return jdbc.queryForList("SELECT id FROM orders ORDER BY id", Integer.class);
    }
}
