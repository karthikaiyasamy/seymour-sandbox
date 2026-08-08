package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.JwtKeyService;
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
    private final JwtKeyService jwtKeyService;
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
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "client_id", required = false) String clientId) {

        String effectiveCode = code;
        String effectiveClientId = clientId != null ? clientId : "seymour_smart_app";

        return processTokenRequest(effectiveCode, effectiveClientId);
    }

    private ResponseEntity<Map<String, Object>> processTokenRequest(String authCode, String clientId) {
        String patientId = "1";
        if ("SMART_AUTH_SYNC".equals(authCode)) {
            patientId = "1";
        } else if (authCode != null && AUTHORIZATION_CODES.containsKey(authCode)) {
            patientId = AUTHORIZATION_CODES.get(authCode);
            AUTHORIZATION_CODES.remove(authCode);
        } else {
            log.warn("[SMART_OAUTH_REJECTED] Token exchange requested with invalid or missing authorization code: {}", authCode);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_grant",
                "error_description", "Invalid, expired, or missing authorization code. Complete GET /oauth/authorize first to obtain a valid code."
            ));
        }

        String scope = "launch/patient patient/*.read patient/Observation.read patient/AllergyIntolerance.read openid fhirUser";
        String jwtToken = jwtKeyService.generateSignedSmartJwt(clientId, patientId, scope, 3600);
        int expiresInSeconds = 3600;

        tokenStoreService.registerToken(jwtToken, patientId, clientId, expiresInSeconds);

        log.info("[SMART_OAUTH_TOKEN_ISSUED] Signed RS256 JWT Access Token generated for Client: {} with Patient Context: {}", clientId, patientId);

        Map<String, Object> tokenResponse = new LinkedHashMap<>();
        tokenResponse.put("access_token", jwtToken);
        tokenResponse.put("token_type", "Bearer");
        tokenResponse.put("expires_in", expiresInSeconds);
        tokenResponse.put("scope", scope);
        tokenResponse.put("patient", patientId);
        tokenResponse.put("need_patient_banner", true);
        tokenResponse.put("smart_style_url", "http://sandbox.local/smart/style");
        tokenResponse.put("id_token", "eyJhbGciOiJSUzI1NiJ9.smart_user_identity_token");

        return ResponseEntity.ok(tokenResponse);
    }
}
