package com.wind.integration.metrics.jdbc;

import com.wind.integration.metrics.dsl.MetricDefinitionDslCodec;
import com.wind.integration.metrics.dsl.definition.MetricDefinitionDsl;
import com.wind.integration.metrics.dsl.definition.MetricDslSpec;
import com.wind.integration.metrics.dsl.definition.selection.MetricLimitDsl;
import com.wind.integration.metrics.dsl.definition.selection.MetricRowSelectionDsl;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 无宿主依赖的 DSL 与已验证物理映射样本。
 *
 * @author wuxp
 */
final class MetricJdbcTestFixtures {
    private MetricJdbcTestFixtures() {}

    static MetricDefinitionDsl scalarCountDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_COUNT",
                    "valueShape": "SCALAR",
                    "fact": "MetricOrderFact",
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["region"],
                    "value": {
                      "valueType": "LONG",
                      "measure": {"aggregation": "COUNT"},
                      "orElse": {"mode": "ZERO"}
                    }
                  }
                }
                """);
    }

    static MetricDefinitionDsl fieldSetDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_SUMMARY",
                    "valueShape": "FIELD_SET",
                    "fact": "MetricOrderFact",
                    "joins": [{
                      "alias": "customer",
                      "fact": "MetricCustomerFact",
                      "joinType": "LEFT",
                      "cardinality": "MANY_TO_ONE",
                      "on": [{"primaryField": "customerId", "joinField": "customerId"}]
                    }],
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["region"],
                    "fields": {
                      "refundedCount": {
                        "valueType": "LONG",
                        "measure": {
                          "aggregation": "COUNT",
                          "filter": {"eq": {"refunded": true}}
                        },
                        "orElse": {"mode": "ZERO"}
                      },
                      "approvedAmount": {
                        "valueType": "DECIMAL",
                        "scale": 4,
                        "roundingMode": "HALF_UP",
                        "measure": {
                          "aggregation": "SUM",
                          "field": "amount",
                          "filter": {"eq": {"status": "APPROVED"}}
                        },
                        "orElse": {"mode": "NULL"}
                      }
                    }
                  }
                }
                """);
    }

    public static MetricDefinitionDsl factExpressionDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_COUNT_SUMMARY",
                    "valueShape": "FIELD_SET",
                    "fact": "MetricOrderFact",
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["region"],
                    "fields": {
                      "orderCount": {
                        "valueType": "LONG",
                        "measure": {"aggregation": "COUNT"},
                        "orElse": {"mode": "ZERO"}
                      },
                      "doubleOrderCount": {
                        "valueType": "LONG",
                        "expression": {
                          "type": "SPEL",
                          "value": "orderCount + orderCount"
                        },
                        "orElse": {"mode": "NULL"}
                      }
                    }
                  }
                }
                """);
    }

    public static MetricDefinitionDsl factRatioDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_APPROVAL_RATE",
                    "valueShape": "FIELD_SET",
                    "fact": "MetricOrderFact",
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["region"],
                    "fields": {
                      "approvedCount": {
                        "valueType": "LONG",
                        "measure": {
                          "aggregation": "COUNT",
                          "filter": {"eq": {"status": "APPROVED"}}
                        },
                        "orElse": {"mode": "ZERO"}
                      },
                      "totalCount": {
                        "valueType": "LONG",
                        "measure": {"aggregation": "COUNT"},
                        "orElse": {"mode": "ZERO"}
                      },
                      "approvalRate": {
                        "valueType": "DECIMAL",
                        "scale": 4,
                        "roundingMode": "HALF_UP",
                        "expression": {
                          "type": "SPEL",
                          "value": "totalCount == 0 ? null : ratio(approvedCount, totalCount)"
                        },
                        "orElse": {"mode": "NULL"}
                      }
                    }
                  }
                }
                """);
    }

    public static MetricDefinitionDsl derivedRatioDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_APPROVAL_RATE_DERIVED",
                    "valueShape": "SCALAR",
                    "subject": {"type": "CUSTOMER"},
                    "dimensions": ["region"],
                    "value": {
                      "valueType": "DECIMAL",
                      "scale": 6,
                      "roundingMode": "HALF_UP",
                      "expression": {
                        "type": "SPEL",
                        "value": "ratio(metric('ORDER_SUMMARY', 'approvedCount'), metric('ORDER_SUMMARY', 'totalCount'))"
                      },
                      "orElse": {"mode": "NULL"}
                    }
                  }
                }
                """);
    }

    static MetricDefinitionDsl numericJoinDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_COUNT_BY_CUSTOMER_NUMBER",
                    "valueShape": "SCALAR",
                    "fact": "MetricOrderFact",
                    "joins": [{
                      "alias": "customer",
                      "fact": "MetricCustomerFact",
                      "joinType": "INNER",
                      "cardinality": "MANY_TO_ONE",
                      "on": [{"primaryField": "customerNumber", "joinField": "customerNumber"}]
                    }],
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["customer.country"],
                    "value": {
                      "valueType": "LONG",
                      "measure": {"aggregation": "COUNT"},
                      "orElse": {"mode": "ZERO"}
                    }
                  }
                }
                """);
    }

    static MetricDefinitionDsl aggregateFieldSetDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_AGGREGATE_SUMMARY",
                    "valueShape": "FIELD_SET",
                    "fact": "MetricOrderFact",
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["region"],
                    "fields": {
                      "averageAmount": {
                        "valueType": "DECIMAL",
                        "scale": 4,
                        "roundingMode": "HALF_UP",
                        "measure": {"aggregation": "AVG", "field": "amount"},
                        "orElse": {"mode": "NULL"}
                      },
                      "maximumQuantity": {
                        "valueType": "INTEGER",
                        "measure": {"aggregation": "MAX", "field": "quantity"},
                        "orElse": {"mode": "NULL"}
                      },
                      "minimumQuantity": {
                        "valueType": "INTEGER",
                        "measure": {"aggregation": "MIN", "field": "quantity"},
                        "orElse": {"mode": "NULL"}
                      }
                    }
                  }
                }
                """);
    }

    public static MetricDefinitionDsl parameterizedRowSelectionDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "FIRST_APPROVED_ORDERS",
                    "valueShape": "FIELD_SET",
                    "fact": "MetricOrderFact",
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["region"],
                    "parameters": {
                      "entryLimit": {
                        "valueType": "INTEGER",
                        "minimum": 1,
                        "maximum": 100
                      }
                    },
                    "rowSelection": {
                      "filter": {
                        "and": [
                          {"eq": {"status": "APPROVED"}},
                          {"eq": {"refunded": false}}
                        ]
                      },
                      "orderBy": [
                        {"field": "occurredAt", "direction": "ASC"},
                        {"field": "id", "direction": "ASC"}
                      ],
                      "limit": {"parameter": "entryLimit"}
                    },
                    "fields": {
                      "approvedAmount": {
                        "valueType": "DECIMAL",
                        "scale": 4,
                        "roundingMode": "HALF_UP",
                        "measure": {"aggregation": "SUM", "field": "amount"},
                        "orElse": {"mode": "ZERO"}
                      },
                      "approvedCount": {
                        "valueType": "LONG",
                        "measure": {"aggregation": "COUNT"},
                        "orElse": {"mode": "ZERO"}
                      }
                    }
                  }
                }
                """);
    }

    static MetricDefinitionDsl doubleJoinDefinition() {
        return parse(
                """
                {
                  "schemaVersion": 1,
                  "metric": {
                    "code": "ORDER_COUNT_BY_REGION_GROUP",
                    "valueShape": "SCALAR",
                    "fact": "MetricOrderFact",
                    "joins": [
                      {
                        "alias": "regionInfo",
                        "fact": "MetricRegionFact",
                        "joinType": "INNER",
                        "cardinality": "MANY_TO_ONE",
                        "on": [
                          {"primaryField": "region", "joinField": "region"},
                          {"primaryField": "country", "joinField": "country"}
                        ]
                      },
                      {
                        "alias": "customer",
                        "fact": "MetricCustomerFact",
                        "joinType": "LEFT",
                        "cardinality": "MANY_TO_ONE",
                        "on": [{"primaryField": "customerId", "joinField": "customerId"}]
                      }
                    ],
                    "subject": {"type": "CUSTOMER", "field": "customerId"},
                    "time": {"field": "occurredAt"},
                    "dimensions": ["regionInfo.groupName"],
                    "value": {
                      "valueType": "LONG",
                      "measure": {"aggregation": "COUNT"},
                      "orElse": {"mode": "ZERO"}
                    }
                  }
                }
                """);
    }

    public static MetricDefinitionDsl fixedRowSelectionDefinition(int limit) {
        MetricDefinitionDsl source = parameterizedRowSelectionDefinition();
        MetricDslSpec metric = source.metric();
        MetricRowSelectionDsl rowSelection = metric.rowSelection();
        return new MetricDefinitionDsl(
                source.schemaVersion(),
                new MetricDslSpec(
                        metric.code(),
                        metric.valueShape(),
                        metric.fact(),
                        metric.joins(),
                        metric.subject(),
                        metric.time(),
                        metric.dimensions(),
                        Map.of(),
                        new MetricRowSelectionDsl(
                                rowSelection.filter(),
                                rowSelection.orderBy(),
                                new MetricLimitDsl(limit, null)),
                        metric.value(),
                        metric.fields()));
    }

    static MetricDefinitionDsl parse(String json) {
        return new MetricDefinitionDslCodec().parse(json);
    }

    static MetricJdbcBinding binding(MetricDslSpec definition) {
        return new MetricJdbcBinding() {
            @Override
            public String tableName(String reference) {
                String fact =
                        reference.isEmpty()
                                ? definition.fact()
                                : definition.joins().stream()
                                        .filter(join -> join.alias().equals(reference))
                                        .findFirst()
                                        .orElseThrow()
                                        .fact();
                return switch (fact) {
                    case "MetricOrderFact" -> "t_metric_order_fact";
                    case "MetricCustomerFact" -> "t_metric_customer_fact";
                    case "MetricRegionFact" -> "t_metric_region_fact";
                    default -> throw new IllegalArgumentException("Unknown fact " + fact);
                };
            }

            @Override
            public String columnName(String reference) {
                String name = reference.substring(reference.lastIndexOf('.') + 1);
                return name.equals("refunded")
                        ? "is_refunded"
                        : name.replaceAll("([a-z])([A-Z])", "$1_$2")
                                .toLowerCase(java.util.Locale.ROOT);
            }

            @Override
            public Class<?> javaType(String reference) {
                String name = reference.substring(reference.lastIndexOf('.') + 1);
                return switch (name) {
                    case "id" -> Long.class;
                    case "customerNumber" -> reference.contains(".") ? Long.class : Integer.class;
                    case "quantity" -> Integer.class;
                    case "amount" -> BigDecimal.class;
                    case "occurredAt" -> LocalDateTime.class;
                    case "refunded" -> Boolean.class;
                    case "status" -> OrderStatus.class;
                    default -> String.class;
                };
            }

            @Override
            public int jdbcType(String reference) {
                Class<?> type = javaType(reference);
                if (type == Integer.class) {
                    return Types.INTEGER;
                }
                if (type == Long.class) {
                    return Types.BIGINT;
                }
                if (type == Boolean.class) {
                    return Types.BOOLEAN;
                }
                if (type == BigDecimal.class) {
                    return Types.DECIMAL;
                }
                if (type == LocalDateTime.class) {
                    return Types.TIMESTAMP;
                }
                return Types.VARCHAR;
            }

            @Override
            public Object toJdbcValue(String reference, Object value) {
                return value instanceof Enum<?> enumeration ? enumeration.name() : value;
            }
        };
    }

    enum OrderStatus {
        APPROVED
    }
}
