package com.wind.integration.infrastructure.kms;

import com.wind.common.jul.WindJulLogFactory;
import com.wind.core.WindCredentialsProvider;
import com.wind.integration.kms.WindCredentialsClient;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.function.SingletonSupplier;

import java.util.logging.Logger;

/**
 * 基于 kms 凭据提供者，例如：阿里云
 *
 * @author wuxp
 * @date 2026-03-27 10:58
 **/
public class DefaultKmsCredentialsProvider implements WindCredentialsProvider {

    private static final Logger LOGGER = WindJulLogFactory.getLogger(DefaultKmsCredentialsProvider.class);

    private final SingletonSupplier<WindCredentialsClient> client = SingletonSupplier.of(() -> {
        LOGGER.info("Init WindCredentialsClient");
        return KmsTextEncryptor.getProviderInstance().getCredentialsClient();
    });

    @Override
    public String getCredentials(@NotBlank String credentialsName) {
        LOGGER.info("Get credentials, credentialsName = " + credentialsName);
        return client.obtain().getCredentialsAsText(credentialsName);
    }

}
