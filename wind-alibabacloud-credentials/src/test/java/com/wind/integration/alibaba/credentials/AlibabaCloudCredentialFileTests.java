package com.wind.integration.alibaba.credentials;

import com.aliyun.credentials.Client;
import com.aliyun.credentials.models.CredentialModel;
import com.wind.common.exception.BaseException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

class AlibabaCloudCredentialFileTests {

    @TempDir
    Path directory;

    @Test
    void testDecryptFileAndPreserveExistingLineFormat() throws IOException {
        Path file = directory.resolve("credential");
        Files.writeString(file, " \n加密凭据\n ");
        AtomicReference<String> encrypted = new AtomicReference<>();

        Client client = AlibabaCloudCredentialsFactory.withCredentialFile(file, content -> {
            encrypted.set(content);
            return "\n \n first-id \nfirst-secret\nignored-third-line\n";
        });
        CredentialModel credential = client.getCredential();

        Assertions.assertEquals("加密凭据", encrypted.get());
        Assertions.assertEquals(" first-id ", credential.getAccessKeyId());
        Assertions.assertEquals("first-secret", credential.getAccessKeySecret());
        Assertions.assertNull(credential.getSecurityToken());
    }

    @Test
    void testDefaultFilePathUsesSuppliedDecryptor() throws IOException {
        AtomicReference<Path> requested = new AtomicReference<>();
        try (MockedStatic<Files> ignored = Mockito.mockStatic(Files.class)) {
            ignored.when(() -> Files.readString(Mockito.any(Path.class))).thenAnswer(invocation -> {
                requested.set(invocation.getArgument(0));
                return "encrypted";
            });

            CredentialModel credential = AlibabaCloudCredentialsFactory.withCredentialFile(content -> {
                Assertions.assertEquals("encrypted", content);
                return "default-id\ndefault-secret";
            }).getCredential();

            Assertions.assertEquals(Path.of("/etc/secrets/alibaba_cloud_credential"), requested.get());
            Assertions.assertEquals("default-id", credential.getAccessKeyId());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "only-id", "\nonly-id\n \n"})
    void testRejectInvalidDecryptedContent(String decrypted) throws IOException {
        Path file = directory.resolve("credential");
        Files.writeString(file, "encrypted");

        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.withCredentialFile(file, content -> decrypted));
    }

    @Test
    void testReadFailurePreservesIoCause() {
        BaseException failure = Assertions.assertThrows(BaseException.class,
                () -> AlibabaCloudCredentialsFactory.withCredentialFile(directory.resolve("missing"), UnaryOperator.identity()));

        Assertions.assertInstanceOf(IOException.class, failure.getCause());
    }

    @Test
    void testDecryptFailureIsNotHidden() throws IOException {
        Path file = directory.resolve("credential");
        Files.writeString(file, "encrypted");
        IllegalStateException failure = new IllegalStateException("test decrypt failure");

        IllegalStateException actual = Assertions.assertThrows(IllegalStateException.class,
                () -> AlibabaCloudCredentialsFactory.withCredentialFile(file, content -> {
                    throw failure;
                }));

        Assertions.assertSame(failure, actual);
    }

    @Test
    void testFileCredentialsAreReadOnce() throws IOException {
        Path file = directory.resolve("credential");
        Files.writeString(file, "first-id\nfirst-secret");
        Client first = AlibabaCloudCredentialsFactory.withCredentialFile(file, UnaryOperator.identity());
        Files.writeString(file, "second-id\nsecond-secret");

        Assertions.assertEquals("first-id", first.getCredential().getAccessKeyId());
        Assertions.assertEquals("second-id", AlibabaCloudCredentialsFactory.withCredentialFile(file, UnaryOperator.identity()).getCredential().getAccessKeyId());
    }

    @Test
    void testRejectMissingFileArguments() {
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.withCredentialFile(null));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.withCredentialFile(null, UnaryOperator.identity()));
        Assertions.assertThrows(BaseException.class, () -> AlibabaCloudCredentialsFactory.withCredentialFile(directory, null));
    }
}
