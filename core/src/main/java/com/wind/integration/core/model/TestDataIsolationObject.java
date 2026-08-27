package com.wind.integration.core.model;

/**
 * 测试正式数据隔离对象，一般用于生产环境需要提供测试用户用于给质量、运营等人员做系统验证或演示
 *
 * @author wuxp
 * @date 2026-08-27 09:31
 **/
public interface TestDataIsolationObject {

    void setTestData(boolean testData);

    /**
     * @return 是否为测试数据
     */
    default boolean isTestData() {
        return false;
    }
}
