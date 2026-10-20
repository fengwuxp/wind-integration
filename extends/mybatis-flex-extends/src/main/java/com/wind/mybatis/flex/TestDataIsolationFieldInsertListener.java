package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.TestDataIsolationObject;

/**
 * 测试数据字段插入时自动设置
 *
 * @author wuxp
 * @date 2026-08-27 09:49
 **/
public class TestDataIsolationFieldInsertListener extends AbstractInsertListener<TestDataIsolationObject> {

    @Override
    public void doInsert(TestDataIsolationObject entity) {
        entity.setTestData(DataIsolationContextHolder.isTestData());
    }
}
