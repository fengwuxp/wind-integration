package com.wind.integration.oss;

import com.wind.integration.oss.model.WindOssFile;
import lombok.AllArgsConstructor;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author wuxp
 * @date 2024-12-27 15:38
 **/
@AllArgsConstructor
public class DefaultBucketOperationsTemplate implements BucketOperationsTemplate {

    private final WindOssClient client;

    private final String bucketName;

    @Override
    public WindOssFile uploadFile(String objectKey, InputStream inputStream, Map<String, String> metadata) throws WindOSSException {
        return client.uploadFile(bucketName, objectKey, inputStream, metadata);
    }

    @Override
    public InputStream downloadFile(String objectKey) throws WindOSSException {
        return client.downloadFile(bucketName, objectKey);
    }

    @Override
    public void deleteFile(String objectKey) throws WindOSSException {
        client.deleteFile(bucketName, objectKey);
    }

    @Override
    public List<String> listFiles(String keyPrefix, int maxKeys) throws WindOSSException {
        return client.listFiles(bucketName, keyPrefix, maxKeys);
    }

    @Override
    public void copyFile(String objectKey, String destBucketName) throws WindOSSException {
        client.copyFile(bucketName, objectKey, destBucketName);
    }

    @Override
    public void copyFile(String sourceKey, String destBucketName, String destKey) throws WindOSSException {
        client.copyFile(bucketName, sourceKey, destBucketName, destKey);
    }

    @Override
    public WindOssFile statFile(String objectKey) {
        return client.statFile(bucketName, objectKey);
    }

    @Override
    public String getFilePath(String objectKey) {
        return client.getFilePath(bucketName, objectKey);
    }

    @Override
    public String getFileLink(String objectKey) {
        return client.getFileLink(bucketName, objectKey);
    }

    @Override
    public <T> T getFileMetadata(String objectKey) throws WindOSSException {
        return client.getFileMetadata(bucketName, objectKey);
    }
}
