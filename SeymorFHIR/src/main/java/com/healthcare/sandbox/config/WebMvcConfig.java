package com.healthcare.sandbox.config;

import com.healthcare.sandbox.interceptor.SmartFhirSecurityInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final SmartFhirSecurityInterceptor securityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityInterceptor)
                .addPathPatterns("/api/fhir/**", "/api/reports/**")
                .excludePathPatterns("/.well-known/**", "/oauth/**", "/h2-console/**", "/error", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**");
    }
}
