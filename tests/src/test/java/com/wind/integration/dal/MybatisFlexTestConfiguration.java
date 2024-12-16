package com.wind.integration.dal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author wuxp
 * @date 2024-12-16 10:12
 **/
@EnableTransactionManagement
@Configuration
@MapperScan({"com.wind.*.dal.mapper"})
public class MybatisFlexTestConfiguration {
}
