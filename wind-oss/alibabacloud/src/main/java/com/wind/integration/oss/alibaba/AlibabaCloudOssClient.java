package com.wind.integration.oss.alibaba;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.aliyun.oss.model.Bucket;
import com.aliyun.oss.model.CopyObjectResult;
import com.aliyun.oss.model.ListObjectsV2Request;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.VoidResult;
import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.oss.WindOSSException;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.model.WindOssFile;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 阿里云 oss client
 * 参见：https://help.aliyun.com/zh/oss/developer-reference/getting-started?spm=a2c4g.11186623.help-menu-31815.d_3_2_0_0.d71bb919i26RHW
 *
 * @author wuxp
 * @date 2024-12-25 15:36
 **/
@Slf4j
@AllArgsConstructor
public class AlibabaCloudOssClient implements WindOssClient {

    private static final String ALIYUN_DOMAIN = "aliyuncs.com";

    private final OSSClient ossClient;


    @Override
    public void createBucket(String bucketName) throws WindOSSException {
        if (!isBucketExists(bucketName)) {
            Bucket response = ossClient.createBucket(bucketName);
            AssertUtils.state(response.getResponse().isSuccessful(),
                    () -> new WindOSSException(response.getResponse().getErrorResponseAsString(), response.getRequestId()));
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws WindOSSException {
        VoidResult response = ossClient.deleteBucket(bucketName);
        AssertUtils.state(response.getResponse().isSuccessful(),
                () -> new WindOSSException(response.getResponse().getErrorResponseAsString(), response.getRequestId()));
    }

    @Override
    public boolean isBucketExists(String bucketName) throws WindOSSException {
        AssertUtils.hasText(bucketName, "argument bucketName must not empty");
        return ossClient.doesBucketExist(bucketName);
    }

    @Override
    public WindOssFile uploadFile(String bucketName, String objectKey, InputStream inputStream, Map<String, String> metadata) throws WindOSSException {
        // 覆盖上传
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setUserMetadata(metadata == null ? Collections.emptyMap() : metadata);
        PutObjectResult response = ossClient.putObject(bucketName, objectKey, inputStream, objectMetadata);
        ResponseMessage message = response.getResponse();
        AssertUtils.state(response.getETag() != null, () -> new WindOSSException(message.getErrorResponseAsString(), response.getRequestId()));
        return statFile(bucketName, objectKey);
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectKey) throws WindOSSException {
        OSSObject response = ossClient.getObject(bucketName, objectKey);
        AssertUtils.state(response.getObjectContent() != null, () -> new WindOSSException(response.getResponse().getErrorResponseAsString(),
                response.getRequestId()));
        return response.getObjectContent();
    }

    @Override
    public void deleteFile(String bucketName, String objectKey) throws WindOSSException {
        VoidResult response = ossClient.deleteObject(bucketName, objectKey);
        AssertUtils.state(response.getResponse().isSuccessful(), () -> new WindOSSException(response.getResponse().getErrorResponseAsString(),
                response.getRequestId()));
    }

    @Override
    public List<String> listFiles(String bucketName, String prefix, int maxKeys) throws WindOSSException {
        ListObjectsV2Request request = new ListObjectsV2Request();
        request.setBucketName(bucketName);
        request.setPrefix(prefix);
        request.setMaxKeys(maxKeys);
        ListObjectsV2Result response = ossClient.listObjectsV2(request);
        return response.getObjectSummaries()
                .stream()
                .map(OSSObjectSummary::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public void copyFile(String bucketName, String objectKey, String destBucketName) throws WindOSSException {
        copyFile(bucketName, objectKey, destBucketName, objectKey);
    }

    @Override
    public void copyFile(String bucketName, String sourceKey, String destBucketName, String destKey) throws WindOSSException {
        CopyObjectResult response = ossClient.copyObject(bucketName, sourceKey, destBucketName, destKey);
        AssertUtils.state(response.getETag() != null, () -> new WindOSSException(response.getResponse().getErrorResponseAsString(),
                response.getRequestId()));
    }

    @Override
    public WindOssFile statFile(String bucketName, String objectKey) {
        ObjectMetadata metadata = ossClient.getObjectMetadata(bucketName, objectKey);
        WindOssFile result = new WindOssFile();
        result.setName(objectKey);
        result.setKey(objectKey);
        result.setETag(metadata.getETag());
        result.setUrl(getFileUrl(bucketName, objectKey));
        result.setHash(metadata.getContentMD5());
        result.setSize(metadata.getContentLength());
        result.setContentType(metadata.getContentType());
        result.setMetadata(metadata.getUserMetadata());
        if (metadata.getLastModified() != null) {
            ZonedDateTime time = metadata.getLastModified().toInstant().atZone(ZoneId.systemDefault());
            result.setLastModified( time.toLocalDateTime());
        }
        return result;
    }

    @Override
    public String getFileUrl(String bucketName, String objectKey) {
        String host = ossClient.getEndpoint().getHost();
        return AlibabaCloudOssProperties.getBucketDomain(bucketName, host).concat(WindConstants.SLASH).concat(objectKey);
    }

    @Override
    public String getFileInternalUrl(String bucketName, String objectKey) {
        String host = ossClient.getEndpoint().getHost();
        return AlibabaCloudOssProperties.getBucketInternalDomain(bucketName, host).concat(WindConstants.SLASH).concat(objectKey);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getFileMetadata(String bucketName, String objectKey) throws WindOSSException {
        return (T) ossClient.getObjectMetadata(bucketName, objectKey);
    }


}
