package com.wind.integration.core.model;

/**
 * 有 id 唯一标识的对象
 *
 * @author wuxp
 * @date 2024-12-15 19:03
 **/
public interface IdObject<ID> {

    ID getId();

    void setId(ID id);
}
