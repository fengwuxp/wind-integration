package com.wind.integration.kms.model.dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KmsSecretDetailsDTOTests {

    @Test
    void testToStringExcludesSecretContentAndRawResponse() {
        KmsSecretDetailsDTO details = new KmsSecretDetailsDTO();
        details.setSecretName("secret-name");
        details.setVersion("version-1");
        details.setContent("secret-content");
        details.setRaw("raw-secret-response");

        String text = details.toString();
        Assertions.assertTrue(text.contains("secret-name"));
        Assertions.assertTrue(text.contains("version-1"));
        Assertions.assertFalse(text.contains("secret-content"));
        Assertions.assertFalse(text.contains("raw-secret-response"));
    }
}
