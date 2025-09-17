package com.wind.integration.infrastructure.redisson;

import com.wind.common.util.ServiceInfoUtils;
import org.redisson.api.NameMapper;

/**
 * redis key 按照环境隔离
 *
 * @author wuxp
 * @date 2023-11-10 07:53
 **/
public record EnvIsolationNameMapper(String env) implements NameMapper {

    public EnvIsolationNameMapper() {
        this(ServiceInfoUtils.getSpringProfilesActive());
    }

    @Override
    public String map(String name) {
        return String.format("%s@%s", env, name);
    }

    @Override
    public String unmap(String name) {
        return name.substring(env.length() + 1);
    }
}
