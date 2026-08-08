package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.JwtKeyService;
import com.healthcare.sandbox.service.TokenStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class OAuth2Controller {

    private final TokenStoreService tokenStoreService;
    private final JwtKeyService jwtKeyService;

    private static final String SIMULATED_AUTH_CODE = "simulated_auth_code_123";

    /**
     * SMART on FHIR Authorize endpoint.
     * Redirects back to the client app with a simulated authorization code.
     */
    @GetMapping("/authorize")
    public RedirectView authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "launch", required = false) String launch) {

        StringBuilder urlBuilder = new StringBuilder(redirectUri);
        urlBuilder.append(redirectUri.contains("?") ? "&" : "?");
        urlBuilder.append("code=").append(SIMULATED_AUTH_CODE);

        if (state != null) {
            urlBuilder.append("&state=").append(state);
        }

        return new RedirectView(urlBuilder.toString());
    }

    /**
     * SMART on FHIR Token endpoint
     * POST /oauth2/token
     */
    @PostMapping(value = "/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "code", required = false) String paramCode,
            @RequestParam(name = "client_id", required = false) String paramClientId,
            org.springframework.http.HttpEntity<String> httpEntity) {

        String effectiveCode = paramCode;
        String effectiveClientId = paramClientId != null ? paramClientId : "seymour_smart_app";

        if (httpEntity != null && httpEntity.getBody() != null) {
            String rawBody = httpEntity.getBody().trim();
            if (rawBody.startsWith("{")) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> json = mapper.readValue(rawBody, Map.class);
                    if (effectiveCode == null && json.containsKey("code")) {
                        effectiveCode = (String) json.get("code");
                    }
                    if (json.containsKey("client_id")) {
                        effectiveClientId = (String) json.get("client_id");
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse JSON body on token request: {}", e.getMessage());
                }
            }
        }

        return processTokenRequest(effectiveCode, effectiveClientId);
    }

    private ResponseEntity<Map<String, Object>> processTokenRequest(String authCode, String clientId) {
        String patientId = "1";
        String scope = "launch/patient patient/*.read patient/Observation.read patient/AllergyIntolerance.read openid fhirUser";
        String jwtToken = jwtKeyService.generateSignedSmartJwt(clientId, patientId, scope, 3600);
        int expiresInSeconds = 3600;

        tokenStoreService.registerToken(jwtToken, patientId, clientId, expiresInSeconds);

        log.info("[SMART_OAUTH2_TOKEN_ISSUED] Signed RS256 JWT Access Token generated via /oauth2/token for Client: {}", clientId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", jwtToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", expiresInSeconds);
        response.put("scope", scope);
        response.put("patient", patientId);
        response.put("need_patient_banner", true);
        response.put("smart_style_url", "http://sandbox.local/smart/style");

        return ResponseEntity.ok(response);
    }
}
