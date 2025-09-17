package com.wind.mybatis.convert;


import com.wind.common.WindConstants;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.lang.Nullable;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Locale 类型处理器
 *
 * @author wuxp
 * @date 2024-12-16 09:49
 **/
public class LocaleTypeHandler extends BaseTypeHandler<Locale> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Locale parameter, JdbcType jdbcType) throws SQLException {
        // 将 Locale 转为字符串（如 en_US）
        ps.setString(i, parameter.toString());
    }

    @Override
    public Locale getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLocale(rs.getString(columnName));
    }

    @Override
    public Locale getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLocale(rs.getString(columnIndex));
    }

    @Override
    public Locale getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLocale(cs.getString(columnIndex));
    }

    @Nullable
    private Locale toLocale(String localeText) {
        if (localeText == null || localeText.trim().isEmpty()) {
            return null;
        }
        String[] parts = localeText.split(WindConstants.UNDERLINE);
        if (parts.length == 1) {
            // 只有语言代码
            return Locale.of(parts[0]);
        } else if (parts.length == 2) {
            // 包含语言和国家
            return Locale.of(parts[0], parts[1]);
        } else if (parts.length == 3) {
            // 包含语言、国家和变体
            return Locale.of(parts[0], parts[1], parts[2]);
        }
        throw new IllegalArgumentException("Invalid locale format: " + localeText);
    }
}
