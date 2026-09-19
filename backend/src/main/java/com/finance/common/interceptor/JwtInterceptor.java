package com.finance.common.interceptor;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.finance.config.JwtConfig;
import com.finance.entity.User;
import com.finance.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private com.finance.common.i18n.I18nUtil i18nUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token == null || token.isEmpty()) {
            // 解析请求头语言获取对应错误消息
            String lang = request.getHeader("lang");
            writeError(response, 401, i18nUtil.getMessage("request.unauthorized", com.finance.common.i18n.I18nUtil.parseLocale(lang)));
            return false;
        }

        try {
            Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaimPresence("userId")
                    .withClaimPresence("tokenVersion")
                    .withClaimPresence("exp")
                    .build();
            DecodedJWT verified = verifier.verify(token);
            Long userId = verified.getClaim("userId").asLong();
            Integer tokenVersion = verified.getClaim("tokenVersion").asInt();
            if (userId == null || userId <= 0 || tokenVersion == null || tokenVersion < 0) {
                writeExpired(response, request);
                return false;
            }
            User user = userMapper.selectById(userId);
            // 签名有效不等于会话有效：改密后的旧版本以及已注销账号都不能访问业务数据。
            if (user == null || Integer.valueOf(1).equals(user.getDeleted())
                    || !tokenVersion.equals(user.getTokenVersion())) {
                writeExpired(response, request);
                return false;
            }
            request.setAttribute("userId", userId);
            return true;
        } catch (JWTVerificationException e) {
            writeExpired(response, request);
            return false;
        }
    }

    private void writeExpired(HttpServletResponse response, HttpServletRequest request) throws Exception {
        String lang = request.getHeader("lang");
        writeError(response, 401, i18nUtil.getMessage("request.expired", com.finance.common.i18n.I18nUtil.parseLocale(lang)));
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        Result<?> result = Result.error(code, message);
        response.getWriter().write(new ObjectMapper().writeValueAsString(result));
    }
}
