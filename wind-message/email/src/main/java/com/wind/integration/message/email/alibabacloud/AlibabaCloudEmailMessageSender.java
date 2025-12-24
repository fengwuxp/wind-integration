package com.wind.integration.message.email.alibabacloud;


import com.aliyun.dm20151123.Client;
import com.aliyun.dm20151123.models.SingleSendMailRequest;
import com.aliyun.dm20151123.models.SingleSendMailResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.wind.common.WindConstants;
import com.wind.common.exception.AssertUtils;
import com.wind.common.exception.BaseException;
import com.wind.common.exception.DefaultExceptionCode;
import com.wind.integration.core.message.MessageSender;
import com.wind.integration.message.email.SimpleEmailMessage;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

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
     * @param accountSupplier 发件人账号提供者
     * @param properties      配置
     */
    public AlibabaCloudEmailMessageSender(@NonNull Supplier<String> accountSupplier, @NonNull AlibabaCloudEmailProperties properties) {
        this.accountSupplier = accountSupplier;
        this.client = buildClient(properties);
    }

    public AlibabaCloudEmailMessageSender(@NonNull String sendAccount, @NonNull AlibabaCloudEmailProperties properties) {
        this(() -> sendAccount, properties);
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
            log.error("aliyun邮件发送失败, 收件邮箱 = {}, message = {}", message.receivers(), exception.getMessage(), exception);
            throw new BaseException("邮件发送失败");
        }
    }

    @Override
    public boolean supports(Class<? extends SimpleEmailMessage> messageType) {
        return SimpleEmailMessage.class.isAssignableFrom(messageType);
    }

    private Client buildClient(AlibabaCloudEmailProperties properties) {
        try {
            Config config = new Config();
            config.setAccessKeyId(properties.getAccessKey());
            config.setAccessKeySecret(properties.getSecretKey());
            config.setEndpoint(properties.getEndpoint());
            return new Client(config);
        } catch (Exception exception) {
            throw new BaseException(DefaultExceptionCode.COMMON_ERROR, "阿里云邮件发送客户端失败", exception);
        }
    }
}
