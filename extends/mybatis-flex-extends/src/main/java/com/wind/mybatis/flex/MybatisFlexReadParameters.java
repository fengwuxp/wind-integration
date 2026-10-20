package com.wind.mybatis.flex;

import com.mybatisflex.core.FlexConsts;
import com.mybatisflex.core.mybatis.TypeHandlerObject;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.jspecify.annotations.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Flex 的 SQL_ARGS 读取参数适配为标准 MyBatis 绑定，供通用读隔离拦截器使用。
 *
 * @author wuxp
 * @since 2026-10-16
 */
public final class MybatisFlexReadParameters {

    private MybatisFlexReadParameters() {
        throw new AssertionError();
    }

    /**
     * 复制参数容器，保留 Flex 自定义 TypeHandler，并切换到标准 ParameterMapping 绑定。
     *
     * @param statement 当前语句
     * @param original  原始绑定 SQL
     * @return 标准绑定 SQL；原查询不使用 Flex SQL_ARGS 时返回原对象
     */
    public static BoundSql adapt(MappedStatement statement, BoundSql original) {
        if (!(original.getParameterObject() instanceof Map<?, ?> parameters)) {
            return original;
        }
        if (!parameters.containsKey(FlexConsts.SQL_ARGS) || parameters.containsKey(FlexConsts.RAW_ARGS)) {
            return original;
        }
        Object value = parameters.get(FlexConsts.SQL_ARGS);
        if (!(value instanceof Object[] arguments) || arguments.length == 0) {
            return original;
        }
        Map<Object, Object> copiedParameters = new LinkedHashMap<>(parameters);
        copiedParameters.put(FlexConsts.RAW_ARGS, Boolean.TRUE);
        Map<String, Object> additional = new LinkedHashMap<>(original.getAdditionalParameters());
        List<ParameterMapping> mappings = new ArrayList<>(arguments.length);
        for (int i = 0; i < arguments.length; i++) {
            String name = "windFlexArgument" + i;
            while (additional.containsKey(name)) {
                name += "Value";
            }
            Object argument = arguments[i];
            Object raw = argument;
            if (argument instanceof TypeHandlerObject typed) {
                raw = typed.getValue();
            }
            Class<?> javaType = raw == null ? Object.class : raw.getClass();
            ParameterMapping.Builder mapping = new ParameterMapping.Builder(statement.getConfiguration(), name, javaType);
            if (argument instanceof TypeHandlerObject typed) {
                mapping.typeHandler(new FlexArgumentTypeHandler(typed));
            } else if (argument == null) {
                mapping.typeHandler(new FlexArgumentTypeHandler(null));
            }
            mappings.add(mapping.build());
            additional.put(name, raw);
        }
        BoundSql result = new BoundSql(statement.getConfiguration(), original.getSql(), mappings, copiedParameters);
        additional.forEach(result::setAdditionalParameter);
        return result;
    }

    /**
     * 仅用于输入参数，保留 Flex 包装的转换器、原值及 JDBC 类型。
     *
     * @author wuxp
     * @since 2026-10-16
     */
    private record FlexArgumentTypeHandler(@Nullable TypeHandlerObject argument) implements TypeHandler<Object> {

        @Override
        public void setParameter(PreparedStatement statement, int index, Object parameter, JdbcType jdbcType) throws SQLException {
            if (argument == null) {
                statement.setObject(index, null);
            } else {
                argument.setParameter(statement, index);
            }
        }

        @Override
        public Object getResult(ResultSet resultSet, String columnName) throws SQLException {
            throw new SQLFeatureNotSupportedException("Input parameter handler only");
        }

        @Override
        public Object getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            throw new SQLFeatureNotSupportedException("Input parameter handler only");
        }

        @Override
        public Object getResult(CallableStatement statement, int columnIndex) throws SQLException {
            throw new SQLFeatureNotSupportedException("Input parameter handler only");
        }
    }
}
