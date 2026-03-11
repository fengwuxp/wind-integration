package com.wind.integration.kms;

import lombok.AllArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 带切片的 KMS 加密客户端
 *
 * @author wuxp
 * @date 2026-03-11
 */
@AllArgsConstructor
public class ChunkingWindCryptoClient implements WindCryptoClient {

    /**
     * 切片分隔符
     */
    private static final String CHUNK_SEPARATOR = " ";

    /**
     * 切片大小（字节）
     */
    private final int chunkSizeBytes;

    private final WindCryptoClient delegate;

    public ChunkingWindCryptoClient(WindCryptoClient delegate) {
        this(6 * 1024 - 1, delegate);
    }

    @Override
    public String encrypt(String keyId, String plaintext, Map<String, Object> options) {
        byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < chunkSizeBytes) {
            return delegate.encrypt(keyId, plaintext, options);
        }

        List<String> encryptedParts = new ArrayList<>();
        int offset = 0;
        while (offset < bytes.length) {
            int end = Math.min(offset + chunkSizeBytes, bytes.length);
            // 避免截断 UTF-8 多字节字符
            while (end < bytes.length && (bytes[end] & 0xC0) == 0x80) {
                end--;
            }
            String part = new String(bytes, offset, end - offset, StandardCharsets.UTF_8);

            encryptedParts.add(delegate.encrypt(keyId, part, options));

            offset = end;
        }

        return String.join(CHUNK_SEPARATOR, encryptedParts);
    }

    @Override
    public String decrypt(String cipherText, Map<String, Object> options) {
        if (cipherText.contains(CHUNK_SEPARATOR)) {
            String[] parts = cipherText.split(CHUNK_SEPARATOR);
            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                result.append(delegate.decrypt(part, options));
            }
            return result.toString();
        }
        return delegate.decrypt(cipherText, options);

    }

    @Override
    public String decrypt(String keyId, String cipherText, Map<String, Object> options) {
        if (cipherText.contains(CHUNK_SEPARATOR)) {
            String[] parts = cipherText.split(CHUNK_SEPARATOR);
            StringBuilder result = new StringBuilder();
            for (String part : parts) {
                result.append(delegate.decrypt(keyId, part, options));
            }
            return result.toString();
        }
        return delegate.decrypt(keyId, cipherText, options);

    }
}