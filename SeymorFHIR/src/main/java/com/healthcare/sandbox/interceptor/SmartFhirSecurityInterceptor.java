package com.healthcare.sandbox.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class SmartFhirSecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        // Validate Bearer token presence (accepts valid SMART tokens or dev tokens)
        if (authHeader != null && (authHeader.startsWith("Bearer eySmartFhirToken_") || authHeader.startsWith("Bearer dev_token"))) {
            log.info("[AUTH_SUCCESS] Valid SMART-on-FHIR Bearer Token presented for route: {}", request.getRequestURI());
            return true; // ALLOW ACCESS
        }

        log.warn("[AUTH_REJECTED] Missing or invalid Authorization header on route: {}", request.getRequestURI());

        // Return HTTP 401 Unauthorized + FHIR OperationOutcome
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/fhir+json");
        response.getWriter().write("""
            {
              "resourceType": "OperationOutcome",
              "issue": [{
                "severity": "error",
                "code": "login",
                "diagnostics": "Unauthorized access to FHIR resource. Valid 'Authorization: Bearer <token>' header required."
              }]
            }
            """);
        return false; // BLOCK REQUEST
    }
}
