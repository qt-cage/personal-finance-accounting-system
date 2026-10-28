package com.finance.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {
    @Test void rejectsMissingBlankAndShortSecretsWithoutDisclosingThem() {
        for (String secret : new String[]{null, "", "   ", "short-test-secret", "short                               "}) {
            JwtConfig config = new JwtConfig(); config.setSecret(secret);
            IllegalStateException error = assertThrows(IllegalStateException.class, config::validate);
            if (secret != null && !secret.trim().isEmpty()) assertFalse(error.getMessage().contains(secret));
        }
    }
    @Test void acceptsConfiguredSecretAndDoesNotExposeItInToString() {
        JwtConfig config = new JwtConfig();
        String secret = "test-only-configuration-secret-at-least-32-bytes";
        config.setSecret(secret);
        assertDoesNotThrow(config::validate);
        assertFalse(config.toString().contains(secret));
    }
}
