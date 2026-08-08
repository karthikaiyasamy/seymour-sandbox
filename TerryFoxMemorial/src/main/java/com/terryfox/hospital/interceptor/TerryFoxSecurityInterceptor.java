package com.terryfox.hospital.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import com.nimbusds.jwt.JWTClaimsSet;
import com.terryfox.hospital.service.TerryFoxJwksKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Interceptor
@RequiredArgsConstructor
@Slf4j
public class TerryFoxSecurityInterceptor {

    private final TerryFoxJwksKeyService jwksKeyService;

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public boolean incomingRequestPreHandled(RequestDetails requestDetails, HttpServletRequest request, HttpServletResponse response) {
        // Allow CORS pre-flight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Allow OpenAPI / Swagger UI / Metadata endpoints
        String requestPath = request.getRequestURI();
        if (requestPath.contains("/v3/api-docs") || requestPath.contains("/swagger-ui") || requestPath.contains("/metadata")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[TERRY_FOX_AUTH_REJECTED] Missing or malformed Authorization Bearer header on route: {}", requestPath);
            throw new AuthenticationException("Missing or invalid Bearer Authorization header. SMART on FHIR OAuth token required.");
        }

        String token = authHeader.substring(7).trim();
        JWTClaimsSet claims = jwksKeyService.verifySignedJwt(token);

        if (claims == null) {
            log.warn("[TERRY_FOX_AUTH_REJECTED] Invalid, unverified, or expired Bearer token presented on route: {}", requestPath);
            throw new AuthenticationException("Invalid, expired, or unverified Bearer JWT token.");
        }

        log.info("[TERRY_FOX_AUTH_SUCCESS] Access granted for Subject: {} on HAPI FHIR route: {}", claims.getSubject(), requestPath);
        return true;
    }
}
