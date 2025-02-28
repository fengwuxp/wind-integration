package com.wind.integration.oss.alibaba;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author wuxp
 * @date 2025-02-28 09:14
 **/
class AlibabaCloudOssClientTests {

    private AlibabaCloudOssClient client = new AlibabaCloudOssClient(null, "cn-hangzhou");

    @Test
    void testGetBucketDomain() {
        Assertions.assertEquals("example.oss-cn-hangzhou-internal.aliyuncs.com", client.getBucketDomain("example", true));
        Assertions.assertEquals("example.oss-cn-hangzhou.aliyuncs.com", client.getBucketDomain("example", false));
    }
}
