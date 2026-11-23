package com.wind.integration.oss.alibaba;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.Config;
import com.aliyun.credentials.models.CredentialModel;
import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.common.auth.Credentials;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentials;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.internal.OSSUtils;
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
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.oss.WindOSSException;
import com.wind.integration.oss.WindOssClient;
import com.wind.integration.oss.model.WindOssFile;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
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
public class AlibabaCloudOssClient implements WindOssClient, AutoCloseable {

    private static final String INTERNAL_FLAG = "-internal.";

    private final OSSClient ossClient;

    /**
     * 供工厂内部封装 OSS 客户端，由本对象负责关闭底层客户端。
     *
     * @param ossClient OSS 客户端
     */
    AlibabaCloudOssClient(OSSClient ossClient) {
        this.ossClient = ossClient;
    }

    /**
     * 创建独立的默认 OSS 连接配置，最大连接数为 256。
     *
     * @return 可由调用方继续调整的 SDK 连接配置
     */
    public static ClientConfiguration getDefaultClientConfiguration() {
        ClientConfiguration result = new ClientConfiguration();
        result.setMaxConnections(256);
        return result;
    }

    /**
     * 获取 Bucket 公网 endpoint，保持原有内网域名转换规则。
     *
     * @param bucketName Bucket 名称
     * @param endpointHost 不带协议的 endpoint host
     * @return Bucket 公网 endpoint
     */
    public static String getBucketEndpoint(String bucketName, String endpointHost) {
        String result;
        if (endpointHost.contains(INTERNAL_FLAG)) {
            // 替换掉 -internal
            result = bucketName + WindConstants.DOT + endpointHost.replaceAll(INTERNAL_FLAG.substring(0, INTERNAL_FLAG.length() - 1), "");
        } else {
            result = bucketName + WindConstants.DOT + endpointHost;
        }
        return OSSUtils.toEndpointURI(result, "https").toString();
    }

    /**
     * 获取 Bucket 内网 endpoint，兼容原有 aliyuncs.com 域名拼装规则。
     * 其他站点或自定义域名应由调用方提供实际内网地址，不使用本方法推导。
     *
     * @param bucketName Bucket 名称
     * @param endpointHost 不带协议的 endpoint host
     * @return Bucket 内网 endpoint
     */
    public static String getBucketInternalEndpoint(String bucketName, String endpointHost) {
        String result;
        if (endpointHost.contains(INTERNAL_FLAG)) {
            result = bucketName + WindConstants.DOT + endpointHost;
        } else {
            // 保留旧工具的后缀规则，避免迁移时改变多级 endpoint 的地址。
            String legacyDomainSuffix = "aliyuncs.com";
            endpointHost = endpointHost.substring(0, endpointHost.length() - legacyDomainSuffix.length() - 1) + INTERNAL_FLAG + legacyDomainSuffix;
            result = bucketName + WindConstants.DOT + endpointHost;
        }
        return OSSUtils.toEndpointURI(result, "https").toString();
    }

    /**
     * 使用公共工厂的推荐环境凭据策略，默认最大连接数为 256。
     *
     * @param endpoint OSS 访问域名
     * @return 由调用方负责关闭的 OSS 客户端
     */
    public static AlibabaCloudOssClient defaults(String endpoint) {
        return defaults(null, endpoint, getDefaultClientConfiguration());
    }

    /**
     * 显式凭据配置交给公共工厂创建，未传配置时使用公共工厂的推荐环境策略。
     *
     * @param credentialConfig    凭据配置，允许为 null
     * @param endpoint            OSS 访问域名
     * @param clientConfiguration OSS 连接配置，直接交给 SDK 使用
     * @return 由调用方负责关闭的 OSS 客户端
     */
    public static AlibabaCloudOssClient defaults(@Nullable Config credentialConfig, String endpoint, ClientConfiguration clientConfiguration) {
        return defaults(credentialConfig, endpoint, null, clientConfiguration);
    }

    /**
     * 使用显式地域装配客户端，适用于需要配置 V4 签名的场景。
     *
     * @param credentialConfig    凭据配置，为 null 时使用公共工厂的推荐环境策略
     * @param endpoint            OSS 访问域名
     * @param region              OSS 地域，为 null 时沿用 SDK 的端点解析结果
     * @param clientConfiguration OSS 连接配置，签名版本由该配置决定
     * @return 由调用方负责关闭的 OSS 客户端
     */
    public static AlibabaCloudOssClient defaults(@Nullable Config credentialConfig, String endpoint, @Nullable String region, ClientConfiguration clientConfiguration) {
        Client credential;
        if (credentialConfig == null) {
            credential = AlibabaCloudCredentialsFactory.defaults();
        } else {
            credential = AlibabaCloudCredentialsFactory.create(credentialConfig);
        }
        return withCredential(credential, endpoint, region, clientConfiguration);
    }

    /**
     * 使用可共享的凭据客户端。请求开始时读取完整凭据，刷新由凭据 SDK 负责。
     *
     * @param credential          调用方持有的共享凭据客户端，不随 OSS 客户端关闭
     * @param endpoint            OSS 访问域名
     * @param clientConfiguration OSS 连接配置，直接交给 SDK 使用
     * @return 由调用方负责关闭的 OSS 客户端
     */
    public static AlibabaCloudOssClient withCredential(Client credential, String endpoint, ClientConfiguration clientConfiguration) {
        return withCredential(credential, endpoint, null, clientConfiguration);
    }

    /**
     * 使用已有凭据和显式地域装配客户端；V4 签名要求地域已提供或可由 SDK 从端点解析。
     *
     * @param credential          已有凭据客户端
     * @param endpoint            OSS 访问域名
     * @param region              OSS 地域，为 null 时沿用 SDK 的端点解析结果
     * @param clientConfiguration OSS 连接配置，签名版本由该配置决定
     * @return 由调用方负责关闭的 OSS 客户端
     */
    public static AlibabaCloudOssClient withCredential(Client credential, String endpoint, @Nullable String region, ClientConfiguration clientConfiguration) {
        AssertUtils.notNull(credential, "共享凭据客户端不能为空");
        AssertUtils.hasText(endpoint, "OSS endpoint 不能为空白");
        AssertUtils.notNull(clientConfiguration, "OSS 连接配置不能为空");
        if (region != null) {
            AssertUtils.hasText(region, "OSS region 不能为空白");
        }
        // SDK 构造时先分配连接池，再检查域名；提前复用相同校验，避免无效域名遗留连接池。
        OSSUtils.ensureEndpointValid(OSSUtils.toEndpointURI(endpoint, clientConfiguration.getProtocol().toString()).getHost());
        CredentialsProvider provider = new CredentialsProvider() {
            @Override
            public Credentials getCredentials() {
                CredentialModel snapshot = credential.getCredential();
                return new DefaultCredentials(snapshot.getAccessKeyId(), snapshot.getAccessKeySecret(), snapshot.getSecurityToken());
            }

            @Override
            public void setCredentials(Credentials credentials) {
                throw new UnsupportedOperationException("共享凭据由凭据 SDK 管理，不支持替换为静态凭据");
            }
        };
        OSSClient client = new OSSClient(endpoint, provider, clientConfiguration);
        try {
            if (region != null) {
                client.setRegion(region);
            }
            if (SignVersion.V4.equals(clientConfiguration.getSignatureVersion())) {
                // 保留 SDK 对 CloudBox 端点的地域解析，再检查 V4 的必需参数。
                AssertUtils.hasText(client.getObjectOperation().getRegion(), "OSS V4 签名缺少 region，请使用带 region 的重载");
            }
            return new AlibabaCloudOssClient(client);
        } catch (RuntimeException exception) {
            client.shutdown();
            throw exception;
        }
    }

    /**
     * 关闭底层 OSS 客户端并释放连接池，不关闭共享凭据。
     */
    @Override
    public void close() {
        ossClient.shutdown();
    }

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
            result.setLastModified(time.toLocalDateTime());
        }
        return result;
    }

    @Override
    public String getFileUrl(String bucketName, String objectKey) {
        String host = ossClient.getEndpoint().getHost();
        return getBucketEndpoint(bucketName, host).concat(WindConstants.SLASH).concat(objectKey);
    }

    @Override
    public String getFileInternalUrl(String bucketName, String objectKey) {
        String host = ossClient.getEndpoint().getHost();
        return getBucketInternalEndpoint(bucketName, host).concat(WindConstants.SLASH).concat(objectKey);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getFileMetadata(String bucketName, String objectKey) throws WindOSSException {
        return (T) ossClient.getObjectMetadata(bucketName, objectKey);
    }

}
