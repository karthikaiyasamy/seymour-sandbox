package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.JwtKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
