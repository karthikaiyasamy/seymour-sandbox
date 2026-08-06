package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.TokenStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class OAuth2TokenController {

    private final TokenStoreService tokenStoreService;
    private static final Map<String, String> AUTHORIZATION_CODES = new HashMap<>();

    /**
     * SMART-on-FHIR Authorization Endpoint
     * GET /oauth/authorize?response_type=code&client_id=my_app&redirect_uri=...&scope=launch/patient&launch=1
     */
    @GetMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorize(
            @RequestParam(name = "response_type", defaultValue = "code") String responseType,
            @RequestParam(name = "client_id", defaultValue = "seymour_smart_app") String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "scope", defaultValue = "launch/patient patient/*.read openid fhirUser") String scope,
            @RequestParam(name = "state", defaultValue = "state_123") String state,
            @RequestParam(name = "launch", defaultValue = "1") String launchPatientId) {

        String authCode = "SMART_AUTH_CODE_" + UUID.randomUUID().toString().substring(0, 8);
        AUTHORIZATION_CODES.put(authCode, launchPatientId);

        log.info("[SMART_AUTHORIZATION] Issued Authorization Code: {} for Patient Launch ID: {} to Client: {}", 
                authCode, launchPatientId, clientId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "AUTHORIZED");
        response.put("code", authCode);
        response.put("state", state);
        response.put("patient_context", launchPatientId);
        response.put("redirect_url", (redirectUri != null ? redirectUri : "http://localhost:3000/callback") 
                + "?code=" + authCode + "&state=" + state);
        response.put("message", "SMART-on-FHIR authorization code successfully generated with patient launch context.");

        return ResponseEntity.ok(response);
    }

    /**
     * SMART-on-FHIR OAuth2 Token Endpoint
     * POST /oauth/token
     */
    @PostMapping(value = "/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(name = "grant_type", defaultValue = "authorization_code") String grantType,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "client_id", defaultValue = "seymour_smart_app") String clientId,
            @RequestBody(required = false) Map<String, Object> jsonBody) {

        String authCode = code;
        if (authCode == null && jsonBody != null) {
            authCode = (String) jsonBody.get("code");
        }

        String patientId = "1";
        if ("SMART_AUTH_SYNC".equals(authCode)) {
            patientId = "1";
        } else if (authCode != null && AUTHORIZATION_CODES.containsKey(authCode)) {
            patientId = AUTHORIZATION_CODES.get(authCode);
            AUTHORIZATION_CODES.remove(authCode); // One-time use
        } else {
            log.warn("[SMART_OAUTH_REJECTED] Token exchange requested with invalid or missing authorization code: {}", authCode);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_grant",
                "error_description", "Invalid, expired, or missing authorization code. Complete GET /oauth/authorize first to obtain a valid code."
            ));
        }

        String rawToken = "eySmartFhirToken_" + UUID.randomUUID().toString().replaceAll("-", "");
        int expiresInSeconds = 3600;

        // Register token in active token store
        tokenStoreService.registerToken(rawToken, patientId, clientId, expiresInSeconds);

        log.info("[SMART_OAUTH_TOKEN_ISSUED] Access Token generated for Client: {} with Patient Context: {}", clientId, patientId);

        Map<String, Object> tokenResponse = new LinkedHashMap<>();
        tokenResponse.put("access_token", rawToken);
        tokenResponse.put("token_type", "Bearer");
        tokenResponse.put("expires_in", expiresInSeconds);
        tokenResponse.put("scope", "launch/patient patient/*.read openid fhirUser");
        tokenResponse.put("patient", patientId);
        tokenResponse.put("need_patient_banner", true);
        tokenResponse.put("smart_style_url", "http://localhost:8090/smart-style.json");
        tokenResponse.put("id_token", "eyJhbGciOiJSUzI1NiJ9.smart_user_identity_token");

        return ResponseEntity.ok(tokenResponse);
    }
}
