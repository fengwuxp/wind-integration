package com.wind.integration.oss;

import com.wind.integration.oss.model.WindOssFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * oss client
 *
 * @author wuxp
 * @date 2024-12-17 18:48
 **/
public interface WindOssClient {

    /**
     * 创建 bucket
     *
     * @param bucketName bucket name
     */
    void createBucket(@NotBlank String bucketName);

    /**
     * 删除 bucket
     *
     * @param bucketName bucket name
     */
    void deleteBucket(@NotBlank String bucketName);

    /**
     * 存储桶是否存在
     *
     * @param bucketName 存储桶名称
     * @return boolean
     */
    boolean isBucketExists(@NotBlank String bucketName);

    /**
     * 上传文件到 OSS
     *
     * @param bucketName  存储桶名称
     * @param objectKey   对象上传到 oss 的 key（文件名）
     * @param inputStream 文件内容的输入流
     * @return 文件元数据
     */
    default WindOssFile uploadFile(@NotBlank String bucketName, @NotBlank String objectKey, @NotNull InputStream inputStream) {
        return uploadFile(bucketName, objectKey, inputStream, Collections.emptyMap());
    }

    /**
     * 上传文件到 OSS
     *
     * @param bucketName  存储桶名称
     * @param objectKey   对象上传到 oss 的 key（文件名）
     * @param inputStream 文件内容的输入流
     * @param metadata    文件的元数据（可选）
     * @return 文件元数据
     */
    WindOssFile uploadFile(@NotBlank String bucketName, @NotBlank String objectKey, @NotNull InputStream inputStream,
                           Map<String, String> metadata);

    /**
     * 下载文件从 OSS
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象的键（文件名）
     * @return 文件的输入流
     * @ 如果下载失败
     */
    InputStream downloadFile(@NotBlank String bucketName, String objectKey);

    /**
     * 删除 OSS 上的文件
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象的键（文件名）
     * @ 如果删除失败
     */
    void deleteFile(String bucketName, String objectKey);

    /**
     * 列出 OSS 存储桶中的文件
     *
     * @param bucketName 存储桶名称
     * @param prefix     文件的前缀（可选）
     * @param maxKeys    最大返回的文件数量
     * @return 文件列表
     * @ 如果列举失败
     */
    List<String> listFiles(String bucketName, String prefix, int maxKeys);

    /**
     * 拷贝文件
     *
     * @param bucketName     存储桶名称
     * @param objectKey      存储桶文件 key
     * @param destBucketName 目标存储桶名称
     */
    void copyFile(String bucketName, String objectKey, String destBucketName);

    /**
     * 拷贝文件
     *
     * @param bucketName     存储桶名称
     * @param sourceKey      存储桶文件 key
     * @param destBucketName 目标存储桶名称
     * @param destKey        目标存储桶文件 key
     */
    void copyFile(String bucketName, String sourceKey, String destBucketName, String destKey);

    /**
     * 获取文件信息
     *
     * @param bucketName 存储桶名称
     * @param objectKey  存储桶文件 key
     * @return InputStream
     */
    WindOssFile statFile(String bucketName, String objectKey);

    /**
     * 获取文件地址 (公网)
     *
     * @param bucketName 存储桶名称
     * @param objectKey  存储桶对象名称
     * @return String
     */
    String getFileUrl(String bucketName, String objectKey);

    /**
     * 获取文件地址 (内网)
     *
     * @param bucketName 存储桶名称
     * @param objectKey  存储桶对象名称
     * @return String
     */
    String getFileInternalUrl(String bucketName, String objectKey);

    /**
     * 获取文件的元数据
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象的键（文件名）
     * @return 文件的元数据
     * @ 如果获取元数据失败
     */
    <T> T getFileMetadata(String bucketName, String objectKey);
}
