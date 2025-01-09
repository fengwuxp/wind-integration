package com.wind.integration.oss;

import com.wind.integration.oss.model.WindOssFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * oss bucket 操作 template
 *
 * @author wuxp
 * @date 2024-12-27 15:14
 **/
public interface BucketOperationsTemplate {

    /**
     * 上传文件到 OSS
     *
     * @param objectKey   对象上传到 oss 的 key（文件名）
     * @param inputStream 文件内容的输入流
     * @param metadata    文件的元数据（可选）
     * @return 文件元数据
     * @throws WindOSSException 如果上传失败
     */
    WindOssFile uploadFile(@NotBlank String objectKey, @NotNull InputStream inputStream, Map<String, String> metadata) throws WindOSSException;

    /**
     * 下载文件从 OSS
     *
     * @param objectKey 对象的键（文件名）
     * @return 文件的输入流
     * @throws WindOSSException 如果下载失败
     */
    InputStream downloadFile(String objectKey) throws WindOSSException;

    /**
     * 删除 OSS 上的文件
     *
     * @param objectKey 对象的键（文件名）
     * @throws WindOSSException 如果删除失败
     */
    void deleteFile(String objectKey) throws WindOSSException;

    /**
     * 列出 OSS 存储桶中的文件
     *
     * @param keyPrefix 文件的前缀（可选）
     * @param maxKeys   最大返回的文件数量
     * @return 文件列表
     * @throws WindOSSException 如果列举失败
     */
    List<String> listFiles(String keyPrefix, int maxKeys) throws WindOSSException;

    /**
     * 拷贝文件
     *
     * @param objectKey      存储桶文件 key
     * @param destBucketName 目标存储桶名称
     */
    void copyFile(String objectKey, String destBucketName) throws WindOSSException;

    /**
     * 拷贝文件
     *
     * @param sourceKey      存储桶文件 key
     * @param destBucketName 目标存储桶名称
     * @param destKey        目标存储桶文件 key
     */
    void copyFile(String sourceKey, String destBucketName, String destKey) throws WindOSSException;

    /**
     * 获取文件信息
     *
     * @param objectKey 存储桶文件 key
     * @return InputStream
     */
    WindOssFile statFile(String objectKey);

    /**
     * 获取文件相对路径
     *
     * @param objectKey 存储桶对象名称
     * @return String
     */
    String getFilePath(String objectKey);

    /**
     * 获取文件地址
     *
     * @param objectKey 存储桶对象名称
     * @return String
     */
    String getFileLink(String objectKey);

    /**
     * 获取文件的元数据
     *
     * @param objectKey 对象的键（文件名）
     * @return 文件的元数据
     * @throws WindOSSException 如果获取元数据失败
     */
    <T> T getFileMetadata(String objectKey) throws WindOSSException;
}
