package com.wind.mybatis.plugin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class IsolationSqlDiagnosticsTests {

    private static final int SQL_DIAGNOSTIC_LIMIT = 16 * 1024;

    @Test
    void testPreservesQueryStructureAndPlaceholders() {
        String sql = "SELECT r.id, r.name FROM records r WHERE r.id = ? AND r.state IN (?, ?) ORDER BY r.id LIMIT ?";

        String result = IsolationSqlDiagnostics.format(sql);

        Assertions.assertEquals("SELECT r . id , r . name FROM records r WHERE r . id = ? AND r . state IN ( ? , ? ) ORDER BY r . id LIMIT ?", result);
    }

    @Test
    void testRedactsStringAndNumericLiterals() {
        String sql = "SELECT 'private-value', 987654, -12.75, 1.234e-19, 0xABCDEF, X'CAFE' FROM records WHERE id = ?";

        String result = IsolationSqlDiagnostics.format(sql);

        Assertions.assertEquals("SELECT <literal> , <literal> , - <literal> , <literal> , <literal> , <literal> FROM records WHERE id = ?", result);
    }

    @Test
    void testRedactsStringPrefixesAndEscapedQuotes() {
        for (String literal : List.of("N'private-value'", "E'private-value'", "R'private-value'", "B'101101'",
                "RB'private-value'", "_utf8'private-value'", "'private''value'", "q'[private'value]'")) {
            Assertions.assertEquals("SELECT <literal>", IsolationSqlDiagnostics.format("SELECT " + literal), literal);
        }
    }

    @Test
    void testRedactsDollarQuotedStringsAndTags() {
        String sql = "SELECT $$private-value$$, $private_tag$private\nvalue$private_tag$";

        Assertions.assertEquals("SELECT <literal> , <literal>", IsolationSqlDiagnostics.format(sql));
    }

    @Test
    void testRedactsAmbiguousDoubleQuotesAndQuotedIdentifiers() {
        String sql = "SELECT \"private-value\", `private-column`, [private-column] FROM records";

        Assertions.assertEquals("SELECT <literal> , <quoted> , <quoted> FROM records", IsolationSqlDiagnostics.format(sql));
    }

    @Test
    void testPreservesSimpleBacktickTableAndColumnNames() {
        String sql = "SELECT `r`.`id` FROM `records` `r` WHERE `r`.`tenant_id` = ? AND `name` = 'private-value'";

        Assertions.assertEquals("SELECT `r` . `id` FROM `records` `r` WHERE `r` . `tenant_id` = ? AND `name` = <literal>",
                IsolationSqlDiagnostics.format(sql));
    }

    @Test
    void testHidesComplexBacktickIdentifiers() {
        for (String name : List.of("private value", "a``b", "a;b", "user@example.com", "123456", "schema.table")) {
            Assertions.assertEquals("SELECT <quoted>", IsolationSqlDiagnostics.format("SELECT `" + name + "`"));
        }
        String multiline = "SELECT `a\nb`";
        Assertions.assertEquals(omitted(multiline, "lexical-error"), IsolationSqlDiagnostics.format(multiline));
    }

    @Test
    void testRemovesCommentsAndNormalizesWhitespace() {
        String sql = "SELECT\r\n id /* private /* nested-private */ tail-private */ FROM\t records "
                + "-- forged-log-entry\nWHERE id = ? // another-private-value\n";

        Assertions.assertEquals("SELECT id FROM records WHERE id = ?", IsolationSqlDiagnostics.format(sql));
    }

    @Test
    void testRedactsLineBreaksInsideStrings() {
        String sql = "SELECT 'private\r\nforged-log-entry', \"private-value\" FROM records";

        Assertions.assertEquals("SELECT <literal> , <literal> FROM records", IsolationSqlDiagnostics.format(sql));
    }

    @Test
    void testOmitsAmbiguousBackslashEscapes() {
        String sql = "SELECT E'private\\'value'";

        Assertions.assertEquals(omitted(sql, "ambiguous-escape"), IsolationSqlDiagnostics.format(sql));
    }

    @Test
    void testOmitsUnsupportedTokensWithoutExposingInput() {
        for (String sql : List.of("SELECT id FROM records #private-value", "SELECT private#value", "SELECT @private_value", "SELECT $private_value")) {
            Assertions.assertEquals(omitted(sql, "unsupported-token"), IsolationSqlDiagnostics.format(sql));
        }
    }

    @Test
    void testOmitsUnsupportedStringDelimitersWithoutLeakingFragments() {
        for (String sql : List.of("SELECT q'!private'value'!'", "SELECT q'<private'value'>'", "SELECT 'private'value'other'")) {
            Assertions.assertEquals(omitted(sql, "unsupported-token"), IsolationSqlDiagnostics.format(sql));
        }
    }

    @Test
    void testOmitsUnsupportedNumericPrefixesAndSuffixes() {
        for (String sql : List.of("SELECT 0b101101", "SELECT 12.345D", "SELECT 1.234e-19f")) {
            Assertions.assertEquals(omitted(sql, "unsupported-token"), IsolationSqlDiagnostics.format(sql));
        }
    }

    @Test
    void testOmitsLexicalErrorsWithoutExposingPartialOutput() {
        for (String sql : List.of("SELECT id FROM records WHERE name = 'private-value", "SELECT \"private-value",
                "SELECT $$private-value", "SELECT $private_tag$private-value", "SELECT id /* private-value")) {
            Assertions.assertEquals(omitted(sql, "lexical-error"), IsolationSqlDiagnostics.format(sql));
        }
    }

    @Test
    void testOmitsLogControlCharacters() {
        for (String control : List.of("\u0000", "\u001B", "\u0085", "\u2028", "\u2029", "\u202E")) {
            String sql = "SELECT private" + control + "value FROM records";
            Assertions.assertEquals(omitted(sql, "control-character"), IsolationSqlDiagnostics.format(sql));
        }
    }

    @Test
    void testOmitsInputAboveLimitWithoutTokenizingOrTruncating() {
        String sql = "SELECT '" + "private-value".repeat(1400) + "'";

        String result = IsolationSqlDiagnostics.format(sql);

        Assertions.assertEquals(omitted(sql, "input-too-long"), result);
        Assertions.assertTrue(result.length() < 100);
    }

    @Test
    void testProcessesInputAtLimitAndBoundsExpandedOutput() {
        String atLimit = "SELECT ?" + " ".repeat(SQL_DIAGNOSTIC_LIMIT - "SELECT ?".length());
        Assertions.assertEquals("SELECT ?", IsolationSqlDiagnostics.format(atLimit));

        String expanded = "SELECT " + "1,".repeat(2000) + "1";
        String result = IsolationSqlDiagnostics.format(expanded);

        Assertions.assertEquals(omitted(expanded, "output-too-long"), result);
        Assertions.assertTrue(result.length() <= SQL_DIAGNOSTIC_LIMIT);
    }

    @Test
    void testProcessesSingleTokenAtInputLimit() {
        String literal = "SELECT '" + "x".repeat(SQL_DIAGNOSTIC_LIMIT - 9) + "'";
        Assertions.assertEquals("SELECT <literal>", IsolationSqlDiagnostics.format(literal));

        String identifier = "SELECT " + "a".repeat(SQL_DIAGNOSTIC_LIMIT - 7);
        Assertions.assertEquals(identifier, IsolationSqlDiagnostics.format(identifier));
    }

    @Test
    void testUnicodeIdentifiersFollowLexerBoundaries() {
        Assertions.assertEquals("SELECT café FROM 数据", IsolationSqlDiagnostics.format("SELECT café FROM 数据"));

        String supplementary = "SELECT " + new String(Character.toChars(0x10400)) + " FROM records";
        Assertions.assertEquals(omitted(supplementary, "lexical-error"), IsolationSqlDiagnostics.format(supplementary));
        String unpaired = "SELECT " + (char) 0xD800;
        Assertions.assertEquals(omitted(unpaired, "lexical-error"), IsolationSqlDiagnostics.format(unpaired));
    }

    @Test
    void testOmitsEmptyAndCommentOnlyInput() {
        for (String sql : List.of("", " \r\n\t", "/* private-value */")) {
            Assertions.assertEquals(omitted(sql, "empty"), IsolationSqlDiagnostics.format(sql));
        }
    }

    private String omitted(String sql, String reason) {
        return "<SQL omitted; length=" + sql.length() + "; reason=" + reason + ">";
    }
}
