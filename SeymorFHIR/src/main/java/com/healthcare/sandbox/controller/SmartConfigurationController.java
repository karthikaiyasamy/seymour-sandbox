package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.JwtKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class SmartConfigurationController {

    private final JwtKeyService jwtKeyService;

    /**
     * GET /.well-known/smart-configuration
     * Official SMART on FHIR v2.0 Well-Known Discovery Endpoint
     */
    @GetMapping("/.well-known/smart-configuration")
    public ResponseEntity<Map<String, Object>> getSmartConfiguration() {
        log.info("Serving /.well-known/smart-configuration discovery metadata");

        Map<String, Object> smartConfig = Map.of(
                "issuer", "http://localhost:8090",
                "authorization_endpoint", "http://localhost:8090/oauth/authorize",
                "token_endpoint", "http://localhost:8090/oauth/token",
                "jwks_uri", "http://localhost:8090/.well-known/jwks.json",
                "grant_types_supported", List.of("authorization_code"),
                "response_types_supported", List.of("code"),
                "scopes_supported", List.of(
                        "openid", "profile", "launch", "launch/patient",
                        "patient/*.read", "patient/*.write", "patient/Patient.read",
                        "patient/Observation.read", "patient/AllergyIntolerance.read"
                ),
                "capabilities", List.of(
                        "launch-standalone",
                        "launch-ehr",
                        "client-public",
                        "client-confidential-symmetric",
                        "context-passthrough-patient",
                        "permission-v2"
                ),
                "code_challenge_methods_supported", List.of("S256")
        );

        return ResponseEntity.ok(smartConfig);
    }

    /**
     * GET /.well-known/jwks.json
     * Exposes public RSA keys for stateless RS256 JWT signature verification
     */
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        log.info("Serving /.well-known/jwks.json public keys");
        return ResponseEntity.ok(jwtKeyService.getJwks());
    }
}
