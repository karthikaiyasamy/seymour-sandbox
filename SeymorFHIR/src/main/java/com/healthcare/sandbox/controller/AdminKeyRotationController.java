package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.JwtKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin Controller for live RSA Key Rotation simulation.
 * 
 * PRODUCTION SECURITY GUARD NOTE:
 * In a production enterprise deployment, this endpoint MUST be protected with strict security controls:
 * 1. Spring Security Role Guard: @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
 * 2. Infrastructure Security: Mutual TLS (mTLS) client certificate verification
 * 3. API Gateway Policy: Expose strictly on internal management VPN / VPC subnet (port 8090/actuator)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AdminKeyRotationController {

    private final JwtKeyService jwtKeyService;

    @PostMapping("/rotate-keys")
    public ResponseEntity<Map<String, Object>> rotateKeys() {
        log.warn("[ADMIN_KEY_ROTATION_REQUEST] Received emergency RSA key rotation request");
        try {
            Map<String, Object> result = jwtKeyService.rotateRsaKeyPair();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Key rotation failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
