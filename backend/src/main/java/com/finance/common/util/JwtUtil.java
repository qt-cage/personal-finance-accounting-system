package com.finance.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.finance.config.JwtConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Date;

@Component
public class JwtUtil {

    @Autowired
    private JwtConfig jwtConfig;

    public String generateToken(Long userId, String username, Integer tokenVersion) {
        if (userId == null || userId <= 0 || tokenVersion == null || tokenVersion < 0) {
            throw new IllegalArgumentException("有效用户和登录版本不能为空");
        }
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("username", username)
                .withClaim("tokenVersion", tokenVersion)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + jwtConfig.getExpiration()))
                .sign(Algorithm.HMAC256(jwtConfig.getSecret()));
    }
}
