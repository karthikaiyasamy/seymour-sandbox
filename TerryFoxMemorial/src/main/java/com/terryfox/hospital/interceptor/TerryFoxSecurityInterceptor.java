package com.terryfox.hospital.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
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
    public void incomingRequestPreHandled(RequestDetails requestDetails) {
        if (requestDetails == null) {
            return;
        }

        HttpServletRequest request = null;
        if (requestDetails instanceof ServletRequestDetails servletRequestDetails) {
            request = servletRequestDetails.getServletRequest();
        }
        
        // Allow CORS pre-flight OPTIONS requests
        if (requestDetails.getRequestType() == ca.uhn.fhir.rest.api.RequestTypeEnum.OPTIONS || (request != null && "OPTIONS".equalsIgnoreCase(request.getMethod()))) {
            return;
        }

        // Allow OpenAPI / Swagger UI / Metadata endpoints
        String requestPath = requestDetails.getCompleteUrl() != null ? requestDetails.getCompleteUrl() : "";
        if (requestDetails.getRestOperationType() == ca.uhn.fhir.rest.api.RestOperationTypeEnum.METADATA || requestPath.contains("/v3/api-docs") || requestPath.contains("/swagger-ui") || requestPath.contains("/metadata")) {
            return;
        }

        String authHeader = requestDetails.getHeader("Authorization");
        if (authHeader == null && request != null) {
            authHeader = request.getHeader("Authorization");
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[TERRY_FOX_AUTH_REJECTED] Missing or malformed Authorization Bearer header on route: {}", requestDetails.getRequestPath());
            throw new AuthenticationException("Missing or invalid Bearer Authorization header. SMART on FHIR OAuth token required.");
        }

        String token = authHeader.substring(7).trim();
        JWTClaimsSet claims = jwksKeyService.verifySignedJwt(token);

        if (claims == null) {
            log.warn("[TERRY_FOX_AUTH_REJECTED] Invalid, unverified, or expired Bearer token presented on route: {}", requestDetails.getRequestPath());
            throw new AuthenticationException("Invalid, expired, or unverified Bearer JWT token.");
        }

        log.info("[TERRY_FOX_AUTH_SUCCESS] Access granted for Subject: {} on HAPI FHIR route: {}", claims.getSubject(), requestDetails.getRequestPath());
    }
}
