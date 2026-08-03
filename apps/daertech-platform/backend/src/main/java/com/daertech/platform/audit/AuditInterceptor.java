package com.daertech.platform.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class AuditInterceptor implements HandlerInterceptor {
    private final JdbcTemplate jdbc;
    public AuditInterceptor(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override
    public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception ex){
        if(!request.getRequestURI().contains("/admin/")) return;
        Authentication auth= SecurityContextHolder.getContext().getAuthentication();
        String actor=auth!=null&&auth.isAuthenticated()?auth.getName():null;
        jdbc.update("INSERT INTO platform.audit_events(id,action,resource_type,resource_id,ip_address,correlation_id,success,details) VALUES (?,?,?,?,?,?,?,jsonb_build_object('method',?,'status',?,'actor',?))",
            UUID.randomUUID(),request.getMethod()+" "+request.getRequestURI(),"HTTP",request.getRequestURI(),clientIp(request),request.getHeader("X-Correlation-Id"),ex==null&&response.getStatus()<400,request.getMethod(),response.getStatus(),actor);
    }
    private String clientIp(HttpServletRequest r){String value=r.getHeader("X-Forwarded-For");return value==null?r.getRemoteAddr():value.split(",")[0].trim();}
}
