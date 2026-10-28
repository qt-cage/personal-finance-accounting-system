package com.finance.common.filter;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.slf4j.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
@Component
public class OperationAuditFilter extends OncePerRequestFilter {
    private static final Logger log=LoggerFactory.getLogger(OperationAuditFilter.class);
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public OperationAuditFilter(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return !r.getServletPath().startsWith("/api/")||"GET".equals(r.getMethod())||"OPTIONS".equals(r.getMethod());}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        ContentCachingResponseWrapper wrapped=new ContentCachingResponseWrapper(response);boolean completed=false;
        try{chain.doFilter(request,wrapped);completed=true;}
        finally{
            try{
                Long actor=(Long)request.getAttribute("userId");int outcome=completed?wrapped.getStatus():500;
                byte[] content=wrapped.getContentAsByteArray();
                if(content.length>0&&content.length<=1024*1024&&wrapped.getContentType()!=null&&wrapped.getContentType().contains("json")){
                    JsonNode body=json.readTree(content);outcome=body.path("code").asInt(outcome);
                    if(actor==null&&request.getServletPath().equals("/api/user/login")&&outcome==200)actor=body.path("data").path("userId").asLong();
                }
                if(actor!=null&&actor>0){
                    // 只记录路由模板、方法与结果；不保存请求体、查询参数、Header或导入/下载令牌。
                    Object pattern=request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    String route=pattern==null?(request.getServletPath().startsWith("/api/admin/")?"/api/admin/*":"/api/*"):pattern.toString();
                    jdbc.update("INSERT INTO operation_log(actor_id,method,route,outcome) VALUES(?,?,?,?)",actor,request.getMethod(),route.substring(0,Math.min(route.length(),160)),outcome);
                }
            }catch(Exception error){log.warn("Audit recording unavailable type={}",error.getClass().getSimpleName());}
            wrapped.copyBodyToResponse();
        }
    }
}
