package com.wind.mybatis.plugin;

import net.sf.jsqlparser.parser.CCJSqlParserConstants;
import net.sf.jsqlparser.parser.CCJSqlParserTokenManager;
import net.sf.jsqlparser.parser.SimpleCharStream;
import net.sf.jsqlparser.parser.StringProvider;
import net.sf.jsqlparser.parser.Token;
import net.sf.jsqlparser.parser.TokenMgrException;
import net.sf.jsqlparser.parser.feature.Feature;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * 隔离日志专用的有界 SQL 诊断视图，不读取绑定值，也不生成可执行 SQL。
 * 字面量和复杂引号标识符隐藏，简单反引号名称保留；方言歧义或词法失败时省略正文。
 *
 * @author wuxp
 * @since 2026-11-10
 */
final class IsolationSqlDiagnostics {

    private static final int MAX_LENGTH = 16 * 1024;

    private static final Pattern SIMPLE_QUOTED_IDENTIFIER_PATTERN = Pattern.compile("`[A-Za-z_][A-Za-z0-9_]*`");

    private IsolationSqlDiagnostics() {
    }

    /**
     * 从词法 token 生成单行诊断信息；省略标记只包含长度与固定原因，不含原文或异常信息。
     *
     * @param sql 待展示的 SQL，不包含从参数对象展开的绑定值
     * @return 脱敏诊断视图或固定格式的省略标记
     */
    static String format(String sql) {
        if (sql.length() > MAX_LENGTH) {
            return omitted(sql.length(), "input-too-long");
        }
        // 未指定数据库方言，不能用一种反斜杠转义规则推断另一种方言的字符串边界。
        if (sql.indexOf('\\') >= 0) {
            return omitted(sql.length(), "ambiguous-escape");
        }
        if (containsLogControl(sql)) {
            return omitted(sql.length(), "control-character");
        }

        CCJSqlParserTokenManager lexer = new CCJSqlParserTokenManager(new SimpleCharStream(new StringProvider(sql), 1, 1, sql.length() + 1));
        lexer.configuration.setValue(Feature.allowDollarQuotedStringTags, true);
        lexer.configuration.setValue(Feature.allowDoubleQuotedStrings, true);
        lexer.configuration.setValue(Feature.allowSquareBracketQuotation, true);
        StringBuilder result = new StringBuilder();
        Token previous = null;
        try {
            for (Token token = lexer.getNextToken(); token.kind != CCJSqlParserConstants.EOF; token = lexer.getNextToken()) {
                if (hasAmbiguousLiteralBoundary(previous, token)) {
                    return omitted(sql.length(), "unsupported-token");
                }
                String text = diagnosticToken(token);
                if (text == null) {
                    return omitted(sql.length(), "unsupported-token");
                }
                int separatorLength = result.isEmpty() ? 0 : 1;
                if (result.length() + separatorLength + text.length() > MAX_LENGTH) {
                    return omitted(sql.length(), "output-too-long");
                }
                if (!result.isEmpty()) {
                    result.append(' ');
                }
                result.append(text);
                previous = token;
            }
        } catch (TokenMgrException exception) {
            return omitted(sql.length(), "lexical-error");
        }
        if (result.isEmpty()) {
            return omitted(sql.length(), "empty");
        }
        return result.toString();
    }

    private static @Nullable String diagnosticToken(Token token) {
        if (token.kind == CCJSqlParserConstants.S_QUOTED_IDENTIFIER) {
            return SIMPLE_QUOTED_IDENTIFIER_PATTERN.matcher(token.image).matches() ? token.image : "<quoted>";
        }
        if (isLiteral(token)) {
            return "<literal>";
        }
        // 词法器会把未支持的数值形式（如 0b101）归为普通标识符，不能原样输出。
        if (token.kind == CCJSqlParserConstants.S_IDENTIFIER && Character.isDigit(token.image.codePointAt(0))) {
            return null;
        }
        // 5.4 的关键字位于数字 token 之前；其后仅允许普通标识符和固定符号。
        boolean structuralToken = token.kind < CCJSqlParserConstants.S_DOUBLE
                || token.kind == CCJSqlParserConstants.S_IDENTIFIER
                || CCJSqlParserConstants.tokenImage[token.kind].startsWith("\"");
        if (!structuralToken || !containsOnlyStructureCharacters(token.image)) {
            return null;
        }
        return token.image;
    }

    // 未支持的引号前缀或数值后缀可能被拆成多个 token，不能只隐藏其中已识别的字面量。
    private static boolean hasAmbiguousLiteralBoundary(@Nullable Token previous, Token current) {
        if (previous == null || previous.endLine != current.beginLine || previous.endColumn + 1 != current.beginColumn) {
            return false;
        }
        // 十六进制 token 会包含尾随空格，该空格仍然是有效边界。
        if (Character.isWhitespace(previous.image.charAt(previous.image.length() - 1))) {
            return false;
        }
        Token adjacent;
        if (isLiteral(previous)) {
            adjacent = current;
        } else if (isLiteral(current)) {
            adjacent = previous;
        } else {
            return false;
        }
        int first = adjacent.image.codePointAt(0);
        return Character.isLetterOrDigit(first) || first == '_';
    }

    private static boolean isLiteral(Token token) {
        return switch (token.kind) {
            case CCJSqlParserConstants.S_CHAR_LITERAL, CCJSqlParserConstants.S_DOLLAR_QUOTED_STRING,
                 CCJSqlParserConstants.S_LONG, CCJSqlParserConstants.S_DOUBLE, CCJSqlParserConstants.S_HEX,
                 CCJSqlParserConstants.K_TRUE, CCJSqlParserConstants.K_FALSE, CCJSqlParserConstants.S_QUOTED_IDENTIFIER -> true;
            default -> false;
        };
    }

    private static boolean isStructureCharacter(int character) {
        return Character.isLetterOrDigit(character) || character == '_'
                || " .,()=<>!+-*/%?|:&^~[]{};".indexOf(character) >= 0;
    }

    private static boolean containsOnlyStructureCharacters(String text) {
        for (int offset = 0; offset < text.length(); ) {
            int character = text.codePointAt(offset);
            if (!isStructureCharacter(character)) {
                return false;
            }
            offset += Character.charCount(character);
        }
        return true;
    }

    private static boolean containsLogControl(String text) {
        for (int offset = 0; offset < text.length(); ) {
            int character = text.codePointAt(offset);
            if (isLogControl(character)) {
                return true;
            }
            offset += Character.charCount(character);
        }
        return false;
    }

    private static boolean isLogControl(int character) {
        if (character == '\r' || character == '\n' || character == '\t') {
            return false;
        }
        int type = Character.getType(character);
        return Character.isISOControl(character) || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static String omitted(int length, String reason) {
        return "<SQL omitted; length=" + length + "; reason=" + reason + ">";
    }
}
