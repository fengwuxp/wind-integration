package com.wind.integration;


import com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration;
import com.wind.common.locks.JdkLockFactory;
import com.wind.common.locks.LockFactory;
import com.wind.common.spring.SpringApplicationContextUtils;
import com.wind.integration.dal.MybatisFlexTestConfiguration;
import com.wind.tools.h2.H2FunctionInitializer;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * 参考文档说明：
 * https://www.yuque.com/suiyuerufeng-akjad/ekxk67/ohnwkqez799ows8b
 * https://juejin.cn/post/7039606720343048206
 *
 * @author wuxp
 */
@SpringJUnitConfig()
@Import({AbstractServiceTest.TestConfig.class})
@ImportAutoConfiguration(value = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        AbstractServiceTest.H2InitializationAutoConfiguration.class,
        SqlInitializationAutoConfiguration.class,
        MybatisFlexAutoConfiguration.class
})
@Transactional(rollbackFor = Exception.class)
@EnableConfigurationProperties()
@TestPropertySource(locations = {"classpath:application-h2.properties", "classpath:application-test.properties"})
@EnableAspectJAutoProxy(proxyTargetClass = true)
public abstract class AbstractServiceTest {


    @Configuration
    @Import({
            MybatisFlexTestConfiguration.class,
            SpringApplicationContextUtils.class})
    static class TestConfig {

        @Bean
        public LockFactory lockFactory() {
            return new JdkLockFactory();
        }

        @Bean
        @Primary
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }

    /**
     * 初始化 h2 不支持的 MYSQL 函数
     */
    @AllArgsConstructor
    @AutoConfiguration
    @AutoConfigureBefore(SqlInitializationAutoConfiguration.class)
    public static class H2InitializationAutoConfiguration {

        private final DataSource dataSource;

        @PostConstruct
        public void init() {
            H2FunctionInitializer.initialize(dataSource);
        }

    }
}
