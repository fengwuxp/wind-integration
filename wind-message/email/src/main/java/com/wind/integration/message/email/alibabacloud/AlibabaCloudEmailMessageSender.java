package com.wind.integration.message.email.alibabacloud;


import com.aliyun.dm20151123.Client;
import com.aliyun.dm20151123.models.SingleSendMailRequest;
import com.aliyun.dm20151123.models.SingleSendMailResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.tea.TeaModel;
import com.aliyun.teaopenapi.models.Config;
import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.integration.core.message.MessageSender;
import com.wind.integration.alibaba.credentials.AlibabaCloudCredentialsFactory;
import com.wind.integration.alibaba.credentials.AlibabaCloudProductProperties;
import com.wind.integration.core.message.definition.SimpleEmailMessage;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;


/**
 * 阿里云邮件发送
 *
 * @author wuxp
 * @date 2025-12-11 16:48
 * <a href="https://help.aliyun.com/zh/direct-mail/api-dm-2015-11-23-overview?scm=20140722.H_2862355._.OR_help-T_cn~zh-V_1">邮件推送</a>
 */
@Slf4j
@AllArgsConstructor
public class AlibabaCloudEmailMessageSender implements MessageSender<SimpleEmailMessage> {

    @NonNull
    private final Client client;

    /**
     * 发件人账号提供者
     */
    @NonNull
    private final Supplier<String> accountSupplier;

    /**
     * 直接使用公共产品配置创建发送器，只选择传入的身份和端点，不读取 Spring 全局配置。
     *
     * @param properties 公共产品配置，认证字段由公共凭据工厂校验
     * @param accountSupplier 发件人账号提供者，每次发送时调用
     * @return 邮件发送器
     */
    public static AlibabaCloudEmailMessageSender fromProperties(AlibabaCloudProductProperties properties, Supplier<String> accountSupplier) {
        AssertUtils.notNull(accountSupplier, "发件人账号提供者不能为空");
        return new AlibabaCloudEmailMessageSender(buildClient(properties), accountSupplier);
    }

    /**
     * 使用公共凭据工厂的推荐环境策略创建发送器，发件账号在每次发送时取得。
     *
     * @param endpoint 邮件推送服务端点
     * @param accountSupplier 发件人账号提供者
     * @return 邮件发送器
     */
    public static AlibabaCloudEmailMessageSender defaults(String endpoint, Supplier<String> accountSupplier) {
        AssertUtils.hasText(endpoint, "邮件服务 endpoint 不能为空白");
        return defaults(null, new Config().setEndpoint(endpoint), accountSupplier);
    }

    /**
     * 显式配置交给公共工厂创建凭据，未传配置时使用推荐环境策略，再统一装配邮件客户端。
     *
     * @param credentialConfig 凭据配置，为 null 时使用公共工厂的 defaults
     * @param clientConfig 邮件连接配置，不得包含身份字段；本方法不修改输入
     * @param accountSupplier 发件人账号提供者，每次发送时调用
     * @return 邮件发送器
     */
    public static AlibabaCloudEmailMessageSender defaults(com.aliyun.credentials.models.@Nullable Config credentialConfig,
                                                         Config clientConfig, Supplier<String> accountSupplier) {
        com.aliyun.credentials.Client credential;
        if (credentialConfig == null) {
            credential = AlibabaCloudCredentialsFactory.defaults();
        } else {
            credential = AlibabaCloudCredentialsFactory.create(credentialConfig);
        }
        return withCredential(credential, clientConfig, accountSupplier);
    }

    /**
     * 使用应用持有的凭据创建发送器，构造时不取证。
     *
     * @param credential 共享的官方凭据客户端，生命周期由调用方管理
     * @param clientConfig 邮件连接配置，不得包含身份字段；本方法不修改输入
     * @param accountSupplier 发件人账号提供者
     * @return 邮件发送器
     */
    public static AlibabaCloudEmailMessageSender withCredential(com.aliyun.credentials.Client credential, Config clientConfig, Supplier<String> accountSupplier) {
        AssertUtils.notNull(credential, "共享凭据客户端不能为空");
        AssertUtils.notNull(clientConfig, "邮件连接配置不能为空");
        AssertUtils.notNull(accountSupplier, "发件人账号提供者不能为空");
        AssertUtils.isNull(clientConfig.getAccessKeyId(), "邮件连接配置不允许设置 accessKeyId");
        AssertUtils.isNull(clientConfig.getAccessKeySecret(), "邮件连接配置不允许设置 accessKeySecret");
        AssertUtils.isNull(clientConfig.getSecurityToken(), "邮件连接配置不允许设置 securityToken");
        AssertUtils.isNull(clientConfig.getBearerToken(), "邮件连接配置不允许设置 bearerToken");
        AssertUtils.isNull(clientConfig.getCredential(), "邮件连接配置不允许设置 credential");
        Config config = TeaModel.toModel(clientConfig.toMap(), new Config()).setCredential(credential);
        return new AlibabaCloudEmailMessageSender(buildClient(config), accountSupplier);
    }

    @SneakyThrows
    @Override
    public void sendMessage(SimpleEmailMessage message) {
        try {
            SingleSendMailRequest request = new SingleSendMailRequest();
            request.setAccountName(accountSupplier.get());
            // 地址类型。取值：0：随机账号 1：发信地址
            request.setAddressType(1);
            request.setReplyToAddress(Boolean.TRUE);
            request.setToAddress(String.join(WindConstants.COMMA, message.receivers()));
            request.setSubject(message.title());
            if (message.isText()) {
                request.setTextBody(message.message());
            } else {
                request.setHtmlBody(message.message());
            }
            SingleSendMailResponse response = client.singleSendMail(request);
            log.debug("aliyun 邮件发送成功, request = {}, response = {}", request, response);
            AssertUtils.isTrue(response.getStatusCode() >= 200 && response.getStatusCode() < 300, "邮件发送失败");
        } catch (TeaException exception) {
            log.error("aliyun 邮件发送失败, 收件邮箱 = {}, message = {}", message.receivers(), exception.getMessage(), exception);
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "邮件发送失败", exception);
        }
    }

    @Override
    public boolean supports(Class<? extends SimpleEmailMessage> messageType) {
        return SimpleEmailMessage.class.isAssignableFrom(messageType);
    }

    private static Client buildClient(AlibabaCloudProductProperties properties) {
        AssertUtils.notNull(properties, "邮件配置不能为空");
        com.aliyun.credentials.Client credential = AlibabaCloudCredentialsFactory.fromProperties(properties);
        return buildClient(new Config().setEndpoint(properties.getEndpoint()).setCredential(credential));
    }

    private static Client buildClient(Config config) {
        try {
            return new Client(config);
        } catch (Exception exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "阿里云邮件发送客户端失败", exception);
        }
    }
}
