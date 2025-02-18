package com.wind.integration.alibaba;

import com.wind.core.WindCredentialsProvider;
import com.wind.integration.kms.WindCredentialsClient;
import com.wind.security.crypto.symmetric.AesTextEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import javax.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阿里云 kms 凭据提供者
 *
 * @author wuxp
 * @date 2025-02-18 15:41
 **/
@Slf4j
public class AlibabaCloudKmsCredentialsProvider implements WindCredentialsProvider {

    public static final AtomicReference<String> KEY = new AtomicReference<>(
            "sI6KGIZ@+6(;p2l130fok1,2..dsk192kjdf81374uj((jj21&7c8^UV_a3SBOJ%");

    public static final AtomicReference<String> SLAT =
            new AtomicReference<>(new String(Hex.encode("923K0DISKKFG2934J".getBytes(StandardCharsets.UTF_8))));

    private final WindCredentialsClient client;

    public AlibabaCloudKmsCredentialsProvider() {
        TextEncryptor encryptor = new AesTextEncryptor(KEY.get(), SLAT.get());
        this.client = AlibabaCloudKmsCryptoClient.of(encryptor::decrypt);
    }

    @Override
    public String getCredentials(@NotBlank String credentialsName) {
        log.debug("get credentials, credentialsName = {}", credentialsName);
        return client.getCredentialsAsText(credentialsName);
    }

}
