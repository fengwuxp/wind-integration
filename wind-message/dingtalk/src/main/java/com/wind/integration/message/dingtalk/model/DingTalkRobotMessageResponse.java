package com.wind.integration.message.dingtalk.model;

import lombok.Data;

import java.beans.Transient;

/**
 * 钉钉机器人消息响应
 * @author wuxp
 * @date 2025-12-11 16:48
 **/
@Data
public class DingTalkRobotMessageResponse {

    /**
     * 错误码
     */
    private String errcode;

    /**
     * 错误信息
     */
    private String errmsg;

    @Transient
    public boolean isSuccess() {
        return "0".equals(errcode);
    }
}
