package com.wind.integration.core.model;

/**
 * 可设置测试数据标记的对象。
 * 在 {@link TestDataIsolationObject} 的读取契约上增加赋值能力，
 * 供实体插入填充等场景使用；标记来源与赋值时机由调用方决定。
 *
 * @author wuxp
 * @since 2026-11-05
 */
public interface SettableTestDataIsolationObject extends TestDataIsolationObject {

    /**
     * 设置对象的测试数据标记。
     *
     * @param testData true 表示测试数据，false 表示正式数据
     */
    void setTestData(boolean testData);
}
