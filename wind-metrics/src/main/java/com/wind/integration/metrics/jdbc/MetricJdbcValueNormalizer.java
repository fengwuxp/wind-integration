package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.MetricValidationException;
import com.wind.integration.metrics.dsl.literal.BooleanMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.DecimalMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.IntegralMetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.MetricLiteralDsl;
import com.wind.integration.metrics.dsl.literal.StringMetricLiteralDsl;
import com.wind.integration.metrics.enums.MetricErrorCode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 指标 JDBC 查询值规范化器，校验查询输入并委托宿主编码为 JDBC 参数。
 *
 * <p>处理主体、时间、维度和过滤字面量；指标查询结果的值类型转换、聚合和表达式求值由包外能力负责。</p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>类型转换：按字段声明归一查询条件值，枚举名称和实例均保留其输入形式</li>
 *   <li>值校验：验证输入值的格式和范围是否符合字段要求</li>
 *   <li>时区转换：将时间值按配置的时区转换为目标时间类型</li>
 *   <li>编码委托：将规范化后的值委托给宿主 codec 进行 JDBC 编码</li>
 * </ul>
 *
 * <h2>支持的值来源</h2>
 * <ul>
 *   <li>主体标识（subject）：字符串、整数、UUID</li>
 *   <li>时间范围（time）：LocalDateTime → 目标时间类型</li>
 *   <li>维度值（dimension）：与声明字段类型匹配的非空值</li>
 *   <li>过滤字面量（literal）：DSL 中的比较值</li>
 * </ul>
 *
 * <h2>支持的目标类型</h2>
 * <ul>
 *   <li>字符串：String, Character</li>
 *   <li>整数：byte、short、int 及包装类型归一为 Integer，long 及包装类型归一为 Long</li>
 *   <li>精确十进制：BigDecimal；整数字面量可精确转换为 BigDecimal，不接收 Float 或 Double</li>
 *   <li>枚举：与声明类型匹配的枚举实例，或通过枚举名称校验的 String</li>
 *   <li>UUID：标准 UUID</li>
 *   <li>布尔：Boolean</li>
 *   <li>时间：LocalDateTime, Instant, OffsetDateTime, ZonedDateTime, Timestamp, Date</li>
 * </ul>
 *
 * <p>不持有字段声明副本，每次规范化都通过 {@link MetricJdbcMapping} 读取字段元信息。
 * 宿主 codec 负责将归一后的输入转换为实际存储值，并校验更窄的物理字段范围。</p>
 *
 * @author wuxp
 */
final class MetricJdbcValueNormalizer {

    private static final Pattern INTEGER_SUBJECT_PATTERN = Pattern.compile("0|[1-9]\\d*");

    private final ZoneId timeZone;

    MetricJdbcValueNormalizer(ZoneId timeZone) {
        this.timeZone = timeZone;
    }

    MetricJdbcParameterBinding subject(MetricJdbcMapping binding, String field, String value) {
        String path = "/subjectId";
        Class<?> type = binding.javaType(field);
        Object normalized;
        if (type == String.class || type == Character.class || type == char.class) {
            if (!value.equals(value.strip())) {
                throw invalid(path, "String subject is not canonical");
            }
            normalized = normalize(type, value, path);
        } else if (type == UUID.class) {
            normalized = uuid(value, path);
        } else if (isInt32(type) || isInt64(type)) {
            if (!INTEGER_SUBJECT_PATTERN.matcher(value).matches()) {
                throw invalid(path, "Subject integer is not canonical");
            }
            normalized = integer(type, new BigInteger(value), path);
        } else {
            throw invalid(path, "Subject field must use a string, integral or UUID type");
        }
        return encode(binding, field, normalized, path);
    }

    MetricJdbcParameterBinding time(MetricJdbcMapping binding, String field, LocalDateTime value) {
        return encode(
                binding,
                field,
                instant(binding.javaType(field), value.atZone(timeZone).toInstant()),
                "/metric/time/field");
    }

    MetricJdbcParameterBinding dimension(MetricJdbcMapping binding, String field, Object value) {
        String path = "/dimensionValues/" + escape(field);
        if (value == null) {
            throw invalid(path, "Dimension value must not be null");
        }
        return encode(binding, field, normalize(binding.javaType(field), value, path), path);
    }

    MetricJdbcParameterBinding literal(MetricJdbcMapping binding, String field, MetricLiteralDsl literal) {
        String path = "/metric/filter";
        Class<?> type = binding.javaType(field);
        Object normalized =
                switch (literal) {
                    case IntegralMetricLiteralDsl integer -> integer(type, integer.value(), path);
                    case DecimalMetricLiteralDsl decimal -> normalize(type, decimal.value(), path);
                    case BooleanMetricLiteralDsl bool -> normalize(type, bool.value(), path);
                    case StringMetricLiteralDsl text ->
                            temporalType(type)
                                    ? temporalLiteral(type, text.value(), path)
                                    : normalize(type, text.value(), path);
                };
        return encode(binding, field, normalized, path);
    }

    private Object normalize(Class<?> type, Object value, String path) {
        if (type == String.class) {
            return value instanceof Character character
                    ? character.toString()
                    : require(value, String.class, path);
        }
        if (type == Character.class || type == char.class) {
            if (value instanceof Character) {
                return value;
            }
            if (value instanceof String text && text.length() == 1) {
                return text.charAt(0);
            }
            throw invalid(path, "Expected a single character value");
        }
        if (type.isEnum()) {
            return enumeration(type, value, path);
        }
        if (type == UUID.class) {
            return uuid(value, path);
        }
        if (type == Boolean.class || type == boolean.class) {
            return require(value, Boolean.class, path);
        }
        if (isInt32(type)) {
            if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
                return ((Number) value).intValue();
            }
            throw invalid(path, "Expected 32-bit integer value");
        }
        if (isInt64(type)) {
            if (value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long) {
                return ((Number) value).longValue();
            }
            throw invalid(path, "Expected 64-bit integer value");
        }
        if (type == BigDecimal.class) {
            return require(value, BigDecimal.class, path);
        }
        if (temporalType(type)) {
            Instant instant =
                    switch (value) {
                        case LocalDateTime local -> local.atZone(timeZone).toInstant();
                        case Instant candidate -> candidate;
                        case OffsetDateTime offset -> offset.toInstant();
                        case ZonedDateTime zoned -> zoned.toInstant();
                        case Timestamp timestamp -> timestamp.toInstant();
                        case Date date -> date.toInstant();
                        default -> throw invalid(path, "Expected temporal value");
                    };
            return instant(type, instant);
        }
        throw invalid(path, "Unsupported fact field Java type");
    }

    private static Object enumeration(Class<?> type, Object value, String path) {
        if (value instanceof Enum<?> enumeration && type.isInstance(enumeration)) {
            return enumeration;
        }
        if (value instanceof String text) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(text)) {
                    return text;
                }
            }
        }
        throw invalid(path, "Enum value type or name does not match fact field");
    }

    private static UUID uuid(Object value, String path) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                UUID uuid = UUID.fromString(text);
                if (uuid.toString().equals(text)) {
                    return uuid;
                }
            } catch (IllegalArgumentException exception) {
                throw invalid(path, "Invalid UUID value");
            }
        }
        throw invalid(path, "UUID value is not canonical");
    }

    private static Object integer(Class<?> type, BigInteger value, String path) {
        try {
            if (isInt32(type)) {
                return value.intValueExact();
            }
            if (isInt64(type)) {
                return value.longValueExact();
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(value);
            }
        } catch (ArithmeticException exception) {
            throw invalid(path, "Integer value is outside field range");
        }
        throw invalid(path, "Integral literal does not match fact field");
    }

    private Object temporalLiteral(Class<?> type, String value, String path) {
        try {
            return instant(type, OffsetDateTime.parse(value).toInstant());
        } catch (DateTimeParseException exception) {
            throw invalid(path, "Expected offset temporal literal");
        }
    }

    private Object instant(Class<?> type, Instant value) {
        if (type == LocalDateTime.class) {
            return LocalDateTime.ofInstant(value, timeZone);
        }
        if (type == Instant.class) {
            return value;
        }
        if (type == OffsetDateTime.class) {
            return value.atZone(timeZone).toOffsetDateTime();
        }
        if (type == ZonedDateTime.class) {
            return value.atZone(timeZone);
        }
        if (type == Timestamp.class) {
            return Timestamp.from(value);
        }
        if (type == Date.class) {
            return Date.from(value);
        }
        throw invalid("/metric/time/field", "Unsupported temporal Java type");
    }

    private static MetricJdbcParameterBinding encode(
            MetricJdbcMapping binding, String field, Object value, String path) {
        try {
            return new MetricJdbcParameterBinding(binding.toJdbcValue(field, value), binding.jdbcType(field));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new MetricValidationException(
                    MetricErrorCode.QUERY_INVALID,
                    path,
                    "Metric value cannot be encoded for its fact field",
                    exception);
        }
    }

    private static <T> T require(Object value, Class<T> type, String path) {
        if (!type.isInstance(value)) {
            throw invalid(path, "Metric value type does not match fact field");
        }
        return type.cast(value);
    }

    private static boolean isInt32(Class<?> type) {
        return type == Byte.class
                || type == byte.class
                || type == Short.class
                || type == short.class
                || type == Integer.class
                || type == int.class;
    }

    private static boolean isInt64(Class<?> type) {
        return type == Long.class || type == long.class;
    }

    private static boolean temporalType(Class<?> type) {
        return type == LocalDateTime.class
                || type == Instant.class
                || type == OffsetDateTime.class
                || type == ZonedDateTime.class
                || type == Timestamp.class
                || type == Date.class;
    }

    private static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static MetricValidationException invalid(String path, String message) {
        return new MetricValidationException(MetricErrorCode.QUERY_INVALID, path, message);
    }
}
