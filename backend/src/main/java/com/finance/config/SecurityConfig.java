package com.finance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import com.finance.common.filter.JwtAuthenticationFilter;
import com.finance.common.filter.OperationAuditFilter;
import com.finance.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Security 配置类
 * 无状态JWT登录；业务接口认证和管理员入口授权由Security过滤链执行。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt,OperationAuditFilter audit,ObjectMapper json) throws Exception {
        http.cors().and().csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .antMatchers(HttpMethod.OPTIONS,"/**").permitAll()
                        .antMatchers("/api/user/login","/api/user/register").permitAll()
                        .antMatchers("/api/admin/**").hasRole("ADMIN")
                        .antMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .logout(logout -> logout.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint((r,s,e)->{
                    s.setStatus(401);s.setContentType("application/json;charset=UTF-8");json.writeValue(s.getWriter(),Result.error(401,"请先登录"));
                }).accessDeniedHandler((r,s,e)->{
                    s.setStatus(403);s.setContentType("application/json;charset=UTF-8");json.writeValue(s.getWriter(),Result.error(403,"该操作需要系统管理员权限"));
                }))
                .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(audit,JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(JwtAuthenticationFilter filter){
        FilterRegistrationBean<JwtAuthenticationFilter> registration=new FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration;
    }
    @Bean
    public FilterRegistrationBean<OperationAuditFilter> auditRegistration(OperationAuditFilter filter){FilterRegistrationBean<OperationAuditFilter> registration=new FilterRegistrationBean<>(filter);registration.setEnabled(false);return registration;}
}
