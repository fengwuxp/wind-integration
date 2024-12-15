package com.wind.orm.core;

import javax.validation.constraints.NotBlank;

/**
 * 支持环境隔离的实体对象
 *
 * @author wuxp
 * @date 2024-12-15 13:07
 **/
public interface EnvIsolationEntity {

    /**
     * @return 环境
     */
    String getEnv();

    /**
     * 设置环境
     *
     * @param env 数据归属的环境
     */
    void setEnv(@NotBlank String env);
}
