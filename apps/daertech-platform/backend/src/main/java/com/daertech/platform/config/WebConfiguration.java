package com.daertech.platform.config;

import com.daertech.platform.audit.AuditInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final AuditInterceptor auditInterceptor;
    public WebConfiguration(AuditInterceptor auditInterceptor){this.auditInterceptor=auditInterceptor;}
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(auditInterceptor).addPathPatterns("/admin/**");}
}
