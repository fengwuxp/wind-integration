package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.context.DataIsolationContextHolder;
import com.wind.integration.core.model.SettableTestDataIsolationObject;

/**
 * 为可设置测试标记的实体填充当前标记，缺少上下文时填 false。
 *
 * @author wuxp
 * @date 2026-08-27 09:49
 **/
public class TestDataIsolationFieldInsertListener extends AbstractInsertListener<SettableTestDataIsolationObject> {

    @Override
    public void doInsert(SettableTestDataIsolationObject entity) {
        entity.setTestData(DataIsolationContextHolder.isTestData());
    }
}
