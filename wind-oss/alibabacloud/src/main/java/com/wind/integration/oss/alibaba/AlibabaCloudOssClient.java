package com.wind.integration.oss.alibaba;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.Bucket;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.VoidResult;
import com.wind.common.exception.AssertUtils;
import com.wind.integration.oss.OSSException;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.model.OssUploadResult;
import com.wind.integration.oss.model.WindOssFile;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    private final OSSClient ossClient;

    @Override
    public void createBucket(String bucketName) throws OSSException {
        if (!isBucketExists(bucketName)) {
            Bucket result = ossClient.createBucket(bucketName);
            AssertUtils.state(result.getResponse().isSuccessful(),
                    () -> new OSSException(result.getResponse().getErrorResponseAsString(), result.getRequestId()));
        }
    }

    @Override
    public void deleteBucket(String bucketName) throws OSSException {
        VoidResult result = ossClient.deleteBucket(bucketName);
        AssertUtils.state(result.getResponse().isSuccessful(),
                () -> new OSSException(result.getResponse().getErrorResponseAsString(), result.getRequestId()));
    }

    @Override
    public boolean isBucketExists(String bucketName) throws OSSException {
        AssertUtils.hasText(bucketName, "argument bucketName must not empty");
        return ossClient.doesBucketExist(bucketName);
    }

    @Override
    public OssUploadResult uploadFile(String bucketName, String objectKey, InputStream inputStream, Map<String, String> metadata) throws OSSException {
        // 覆盖上传
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setUserMetadata(metadata == null ? Collections.emptyMap() : metadata);
        PutObjectResult response = ossClient.putObject(bucketName, objectKey, inputStream, objectMetadata);
        if (!response.getResponse().isSuccessful()) {
            // 尝试做一次重试
            response = ossClient.putObject(bucketName, objectKey, inputStream, objectMetadata);
        }
        AssertUtils.isTrue(response.getResponse().isSuccessful(), () -> String.format("upload file to oss error, bucketName = %s key = %s",
                bucketName, objectKey));
        return new OssUploadResult(objectKey, response.getETag(), LocalDateTime.now());
    }

    @Override
    public InputStream downloadFile(String bucketName, String objectKey) throws OSSException {
        return null;
    }

    @Override
    public void deleteFile(String bucketName, String objectKey) throws OSSException {

    }

    @Override
    public List<String> listFiles(String bucketName, String prefix, int maxKeys) throws OSSException {
        return Collections.emptyList();
    }

    @Override
    public void copyFile(String bucketName, String fileName, String destBucketName) throws OSSException {
        ossClient.copyObject(bucketName, fileName, bucketName, fileName);
    }

    @Override
    public void copyFile(String bucketName, String fileName, String destBucketName, String destFileName) throws OSSException {
        ossClient.copyObject(bucketName, fileName, destBucketName, destFileName);
    }

    @Override
    public WindOssFile statFile(String fileName) {
        return null;
    }

    @Override
    public WindOssFile statFile(String bucketName, String fileName) {
        return null;
    }

    @Override
    public String getFilePath(String fileName) {
        return "";
    }

    @Override
    public String getFilePath(String bucketName, String fileName) {
        return "";
    }

    @Override
    public String getFileLink(String fileName) {
        return "";
    }

    @Override
    public String getFileLink(String bucketName, String fileName) {
        return "";
    }

    @Override
    public Map<String, String> getFileMetadata(String bucketName, String objectKey) throws OSSException {
        return Collections.emptyMap();
    }
}
