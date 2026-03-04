package com.wind.integration.message.email.alibabacloud;

import com.wind.integration.message.email.SimpleEmailMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * @author wuxp
 * @date 2026-03-04 17:55
 **/
@Disabled
class AlibabaCloudEmailMessageSenderTests {

    private AlibabaCloudEmailMessageSender sender;


    @BeforeEach
    void setup() {
        AlibabaCloudEmailProperties properties = new AlibabaCloudEmailProperties();
        properties.setAccessKey("test");
        properties.setSecretKey("test");
        properties.setEndpoint("dm.us-east-1.aliyuncs.com");
        sender = new AlibabaCloudEmailMessageSender("test@email.com", properties);
    }

    @Test
    void testSend() {
        sender.sendMessage(new SimpleEmailMessage("<EMAIL>", "测试", "text", Collections.singleton("1234@emial.com")));
    }
}
