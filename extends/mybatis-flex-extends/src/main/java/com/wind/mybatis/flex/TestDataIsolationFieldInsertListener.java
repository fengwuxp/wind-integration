package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.integration.core.model.TestDataIsolationObject;
import com.wind.trace.WindTraceContext;
import com.wind.trace.WindTracer;

import static com.wind.common.WindConstants.TRACE_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME;

/**
 * 测试数据字段插入时自动设置
 *
 * @author wuxp
 * @date 2026-08-27 09:49
 **/
public class TestDataIsolationFieldInsertListener extends AbstractInsertListener<TestDataIsolationObject> {

    @Override
    public void doInsert(TestDataIsolationObject entity) {
        WindTraceContext context = WindTracer.TRACER.currentContext().orElseGet(WindTraceContext::root);
        entity.setTestData(Boolean.TRUE.equals(context.getContextVariable(TRACE_TEST_DATA_CLASSIFICATION_ATTRIBUTE_NAME, false)));
    }
}
