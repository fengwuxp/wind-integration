package com.wind.integration.oss;

import com.wind.integration.oss.model.OssUploadResult;
import com.wind.integration.oss.model.WindOssFile;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.InputStream;
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
    void createBucket(@NotBlank String bucketName) throws OSSException;

    /**
     * 存储桶是否存在
     *
     * @param bucketName 存储桶名称
     * @return boolean
     */
    boolean isBucketExists(@NotBlank String bucketName) throws OSSException;

    /**
     * 上传文件到 OSS
     *
     * @param bucketName  存储桶名称
     * @param objectKey   对象的键（文件名）
     * @param inputStream 文件内容的输入流
     * @param metadata    文件的元数据（可选）
     * @return 上传结果信息
     * @throws OSSException 如果上传失败
     */
    OssUploadResult uploadFile(@NotBlank String bucketName, @NotBlank String objectKey, @NotNull InputStream inputStream,
                               Map<String, String> metadata) throws OSSException;

    /**
     * 下载文件从 OSS
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象的键（文件名）
     * @return 文件的输入流
     * @throws OSSException 如果下载失败
     */
    InputStream downloadFile(@NotBlank String bucketName, String objectKey) throws OSSException;

    /**
     * 删除 OSS 上的文件
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象的键（文件名）
     * @throws OSSException 如果删除失败
     */
    void deleteFile(String bucketName, String objectKey) throws OSSException;

    /**
     * 列出 OSS 存储桶中的文件
     *
     * @param bucketName 存储桶名称
     * @param prefix     文件的前缀（可选）
     * @param maxKeys    最大返回的文件数量
     * @return 文件列表
     * @throws OSSException 如果列举失败
     */
    List<String> listFiles(String bucketName, String prefix, int maxKeys) throws OSSException;

    /**
     * 拷贝文件
     *
     * @param bucketName     存储桶名称
     * @param fileName       存储桶文件名称
     * @param destBucketName 目标存储桶名称
     */
    void copyFile(String bucketName, String fileName, String destBucketName) throws OSSException;

    /**
     * 拷贝文件
     *
     * @param bucketName     存储桶名称
     * @param fileName       存储桶文件名称
     * @param destBucketName 目标存储桶名称
     * @param destFileName   目标存储桶文件名称
     */
    void copyFile(String bucketName, String fileName, String destBucketName, String destFileName) throws OSSException;

    /**
     * 获取文件信息
     *
     * @param fileName 存储桶文件名称
     * @return InputStream
     */
    WindOssFile statFile(String fileName);

    /**
     * 获取文件信息
     *
     * @param bucketName 存储桶名称
     * @param fileName   存储桶文件名称
     * @return InputStream
     */
    WindOssFile statFile(String bucketName, String fileName);

    /**
     * 获取文件相对路径
     *
     * @param fileName 存储桶对象名称
     * @return String
     */
    String getFilePath(String fileName);

    /**
     * 获取文件相对路径
     *
     * @param bucketName 存储桶名称
     * @param fileName   存储桶对象名称
     * @return String
     */
    String getFilePath(String bucketName, String fileName);

    /**
     * 获取文件地址
     *
     * @param fileName 存储桶对象名称
     * @return String
     */
    String getFileLink(String fileName);

    /**
     * 获取文件地址
     *
     * @param bucketName 存储桶名称
     * @param fileName   存储桶对象名称
     * @return String
     */
    String getFileLink(String bucketName, String fileName);

    /**
     * 获取文件的元数据
     *
     * @param bucketName 存储桶名称
     * @param objectKey  对象的键（文件名）
     * @return 文件的元数据
     * @throws OSSException 如果获取元数据失败
     */
    Map<String, String> getFileMetadata(String bucketName, String objectKey) throws OSSException;
}
