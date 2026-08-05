package com.healthcare.sandbox.interceptor;

import com.healthcare.sandbox.service.TokenStoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmartFhirSecurityInterceptor implements HandlerInterceptor {

    private final TokenStoreService tokenStoreService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            // Validate against TokenStoreService registry
            if (tokenStoreService.isValidToken(token)) {
                log.info("[AUTH_SUCCESS] Valid active SMART-on-FHIR Bearer Token presented for route: {}", request.getRequestURI());
                return true; // ALLOW ACCESS
            }
        }

        log.warn("[AUTH_REJECTED] Missing, unregistered, or expired Authorization token presented on route: {}", request.getRequestURI());

        // Return HTTP 401 Unauthorized + FHIR OperationOutcome
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/fhir+json");
        response.getWriter().write("""
            {
              "resourceType": "OperationOutcome",
              "issue": [{
                "severity": "error",
                "code": "login",
                "diagnostics": "Unauthorized access to FHIR resource. Valid registered 'Authorization: Bearer <token>' required."
              }]
            }
            """);
        return false; // BLOCK REQUEST
    }
}
