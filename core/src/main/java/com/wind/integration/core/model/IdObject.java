package com.wind.integration.core.model;

import java.io.Serializable;

/**
 * 有 id 唯一标识的对象
 *
 * @param <I> id 类型
 * @author wuxp
 * @date 2024-12-15 19:03
 **/
public interface IdObject<I extends Serializable> {

    I getId();
}
