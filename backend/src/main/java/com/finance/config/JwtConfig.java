package com.finance.config;

import lombok.Data;
import lombok.ToString;
import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    @ToString.Exclude
    private String secret;
    private long expiration;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.trim().isEmpty()
                || secret.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be configured with at least 32 bytes of random secret material");
        }
    }
}
