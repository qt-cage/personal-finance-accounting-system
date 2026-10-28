package com.finance.common.filter;
import com.finance.common.interceptor.JwtInterceptor;
import com.finance.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log=LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtInterceptor verifier;private final ObjectMapper json;
    public JwtAuthenticationFilter(JwtInterceptor verifier,ObjectMapper json){this.verifier=verifier;this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){String path=r.getServletPath();return !path.startsWith("/api/")||path.equals("/api/user/login")||path.equals("/api/user/register")||"OPTIONS".equals(r.getMethod());}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        try{
            if(!verifier.preHandle(request,response,null))return;
        }catch(Exception error){
            log.warn("Session validation unavailable type={}",error.getClass().getSimpleName());response.setStatus(500);response.setContentType("application/json;charset=UTF-8");json.writeValue(response.getWriter(),Result.error(500,"暂时无法验证会话，请稍后重试"));return;
        }
        Object user=request.getAttribute("userId"),role=request.getAttribute("userRole");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,Collections.singletonList(new SimpleGrantedAuthority("ROLE_"+role))));
        try{chain.doFilter(request,response);}finally{SecurityContextHolder.clearContext();}
    }
}
