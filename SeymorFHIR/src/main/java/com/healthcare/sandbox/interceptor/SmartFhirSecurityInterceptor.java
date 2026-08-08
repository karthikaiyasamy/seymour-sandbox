package com.healthcare.sandbox.interceptor;

import com.healthcare.sandbox.service.JwtKeyService;
import com.healthcare.sandbox.service.TokenStoreService;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmartFhirSecurityInterceptor implements HandlerInterceptor {

    private final TokenStoreService tokenStoreService;
    private final JwtKeyService jwtKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            // 1. Direct TokenStore Registry Check
            if (tokenStoreService.isValidToken(token)) {
                log.info("[AUTH_SUCCESS] Valid active Bearer Token presented for route: {}", request.getRequestURI());
                return true;
            }

            // 2. Cryptographic RS256 JWT Verification
            try {
                SignedJWT signedJWT = SignedJWT.parse(token);
                RSASSAVerifier verifier = new RSASSAVerifier(jwtKeyService.getPublicRsaKey());

                if (signedJWT.verify(verifier)) {
                    Date exp = signedJWT.getJWTClaimsSet().getExpirationTime();
                    if (exp != null && exp.after(new Date())) {
                        String scope = signedJWT.getJWTClaimsSet().getStringClaim("scope");
                        String requestUri = request.getRequestURI();

                        // Granular SMART Scope Check
                        if (isScopeAuthorized(requestUri, scope)) {
                            log.info("[AUTH_SUCCESS] Valid RS256 Signed JWT with Scope [{}] presented for route: {}", scope, requestUri);
                            return true;
                        } else {
                            log.warn("[AUTH_FORBIDDEN] JWT Scope [{}] insufficient for route: {}", scope, requestUri);
                        }
                    }
                }
            } catch (Exception ex) {
                // Not a JWT or signature invalid
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

    private boolean isScopeAuthorized(String uri, String scope) {
        if (scope == null) return false;
        if (scope.contains("patient/*.read") || scope.contains("user/*.read") || scope.contains("system/*.read")) return true;

        if (uri.contains("/Observation") && scope.contains("Observation.read")) return true;
        if (uri.contains("/Patient") && scope.contains("Patient.read")) return true;
        if (uri.contains("/AllergyIntolerance") && scope.contains("AllergyIntolerance.read")) return true;
        if (uri.contains("/Encounter") && scope.contains("Encounter.read")) return true;

        return false;
    }
}
