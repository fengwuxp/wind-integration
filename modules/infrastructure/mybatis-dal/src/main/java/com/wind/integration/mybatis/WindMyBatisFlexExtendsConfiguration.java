package com.wind.integration.mybatis;

import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.query.QueryColumnBehavior;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import com.wind.common.annotations.VisibleForTesting;
import com.wind.common.exception.AssertUtils;
import com.wind.mybatis.convert.LocaleTypeHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Objects;

/**
 * @author wuxp
 * @date 2024-12-16 09:54
 **/
@Configuration
public class WindMyBatisFlexExtendsConfiguration implements MyBatisFlexCustomizer {

    @VisibleForTesting
    public static final int IN_OP_MAX_SIZE = 1000;

    @VisibleForTesting
    public static final String IN_OP_SIZE_ERROR_MESSAGE = "database query in op size range in >=1 && <=1000";

    static {
        QueryColumnBehavior.setIgnoreFunction(val -> {
            if (val instanceof String) {
                // 如果是空字符串，则忽略
                return !StringUtils.hasText((String) val);
            }
            boolean result = Objects.isNull(val);
            if (!result) {
                if (val instanceof Collection) {
                    // 集合类型，认为是 in 操作
                    AssertUtils.isTrue(((Collection<?>) val).size() < IN_OP_MAX_SIZE, IN_OP_SIZE_ERROR_MESSAGE);
                }
                if (val.getClass().isArray()) {
                    // 数组类型，认为是 in 操作
                    AssertUtils.isTrue(Array.getLength(val) < IN_OP_MAX_SIZE, IN_OP_SIZE_ERROR_MESSAGE);
                }
            }
            return result;
        });
    }

    @Override
    public void customize(FlexGlobalConfig globalConfig) {
        globalConfig.getConfiguration().getTypeHandlerRegistry().register(LocaleTypeHandler.class);

        // 设置全局 ID 生成策略为数据库自增
        FlexGlobalConfig.KeyConfig keyConfig = new FlexGlobalConfig.KeyConfig();
        keyConfig.setKeyType(KeyType.Auto);
        FlexGlobalConfig.getDefaultConfig().setKeyConfig(keyConfig);
        globalConfig.setKeyConfig(keyConfig);
    }

}