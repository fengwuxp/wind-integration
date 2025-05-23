package com.wind.integration.oss.alibaba;

import com.aliyun.oss.internal.OSSUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

/**
 * @author wuxp
 * @date 2025-02-28 11:03
 **/
class AlibabaCloudOssPropertiesTests {

    @Test
    void testGetBucketEndpoint() {
        Assertions.assertEquals("https://example.oss-cn-hangzhou.aliyuncs.com",
                AlibabaCloudOssProperties.getBucketEndpoint("example", "oss-cn-hangzhou.aliyuncs.com"));
        Assertions.assertEquals("https://example.oss-cn-hangzhou.aliyuncs.com",
                AlibabaCloudOssProperties.getBucketEndpoint("example", "oss-cn-hangzhou-internal.aliyuncs.com"));
        Assertions.assertEquals("https://example.oss-cn-hangzhou-internal.aliyuncs.com", AlibabaCloudOssProperties.getBucketInternalEndpoint(
                "example", "oss" +
                        "-cn-hangzhou.aliyuncs.com"));
        Assertions.assertEquals("https://example.oss-cn-hangzhou-internal.aliyuncs.com", AlibabaCloudOssProperties.getBucketInternalEndpoint(
                "example", "oss" +
                        "-cn-hangzhou-internal.aliyuncs.com"));
    }

    @Test
    void testEndpoint() {
        String endpoint = "https://oss-cn-hangzhou-internal.aliyuncs.com";
        URI url = OSSUtils.toEndpointURI(endpoint, "https");
        Assertions.assertEquals("oss-cn-hangzhou-internal.aliyuncs.com", url.getHost());
    }
}
