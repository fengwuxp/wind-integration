package com.wind.integration.core.workflow;

import lombok.Data;

import java.io.Serializable;

/**
 * @author wuxp
 * @date 2025-12-29 11:10
 **/
@Data
public class WorkflowActor implements Serializable {

    /**
     * 策略
     */
    private WorkflowActorStrategy strategy;

    /**
     * 值，例如用户名、角色名或表达式
     */
    private String value;

}
