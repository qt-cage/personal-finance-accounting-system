package com.finance.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtPlaceholderConfigTest {
    private String resolve(String value, boolean environmentVariable) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove("systemEnvironment");
        env.getPropertySources().remove("systemProperties");
        if (value != null) {
            env.getPropertySources().addFirst(environmentVariable
                    ? new SystemEnvironmentPropertySource("test-env", Collections.singletonMap("JWT_SECRET", value))
                    : new MapPropertySource("test-file", Collections.singletonMap("JWT_SECRET", value)));
        }
        env.getPropertySources().addLast(new YamlPropertySourceLoader()
                .load("application", new FileSystemResource("src/main/resources/application.yml")).get(0));
        return Binder.get(env).bind("jwt.secret", String.class).orElse("");
    }

    @Test void fileAndEnvironmentValuesAreBoundWithoutExtraCharacters() throws Exception {
        String valid = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        for (boolean environmentVariable : new boolean[]{false, true}) {
            assertEquals(valid, resolve(valid, environmentVariable));
            JwtConfig shortConfig = new JwtConfig();
            shortConfig.setSecret(resolve(valid.substring(0, 31), environmentVariable));
            assertThrows(IllegalStateException.class, shortConfig::validate);
            JwtConfig validConfig = new JwtConfig();
            validConfig.setSecret(resolve(valid, environmentVariable));
            assertDoesNotThrow(validConfig::validate);
        }
        JwtConfig missing = new JwtConfig();
        missing.setSecret(resolve(null, false));
        assertThrows(IllegalStateException.class, missing::validate);
    }
}
