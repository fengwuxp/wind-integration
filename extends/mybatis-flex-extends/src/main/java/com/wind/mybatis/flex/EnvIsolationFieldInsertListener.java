package com.wind.mybatis.flex;

import com.mybatisflex.annotation.AbstractInsertListener;
import com.wind.common.WindConstants;
import com.wind.common.util.ServiceInfoUtils;
import com.wind.orm.core.EnvIsolationEntity;

/**
 * 环境隔离字段 {@link com.mybatisflex.annotation.InsertListener}
 *
 * @author wuxp
 * @date 2024-12-15 13:09
 **/
public class EnvIsolationFieldInsertListener extends AbstractInsertListener<EnvIsolationEntity> {

    @Override
    public void doInsert(EnvIsolationEntity entity) {
        String env = ServiceInfoUtils.getSpringProfilesActive();
        entity.setEnv(env == null ? WindConstants.UNKNOWN : env);
    }

}
