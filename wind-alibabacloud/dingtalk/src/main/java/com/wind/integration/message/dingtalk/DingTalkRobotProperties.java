package com.wind.integration.message.dingtalk;

import lombok.Data;

/**
 * 钉钉机器人配置
 *
 * @author wuxp
 * @date 2026-11-25 10:36
 **/
@Data
public class DingTalkRobotProperties {

    /**
     * 机器人名称
     */
    private String name;

    /**
     * 机器人 access token
     */
    private String accessToken;

    /**
     * 机器人秘钥
     */
    private String secret;
}
