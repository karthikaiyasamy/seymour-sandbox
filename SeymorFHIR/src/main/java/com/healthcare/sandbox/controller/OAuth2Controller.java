package com.healthcare.sandbox.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
@CrossOrigin(origins = "*")
public class OAuth2Controller {

    // Simulated authorization code mapped to patient ID context.
    // In a real EHR SMART on FHIR flow, the authorization code links to the patient context active in the EHR.
    private static final String SIMULATED_AUTH_CODE = "simulated_auth_code_123";
    private static final String SIMULATED_ACCESS_TOKEN = "simulated-access-token-998877";

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
     * SMART on FHIR Token endpoint.
     * Exchanges the authorization code for an access token containing patient launch context.
     */
    @PostMapping(value = "/token", consumes = "application/x-www-form-urlencoded")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("code") String code,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("client_id") String clientId) {

        if (!SIMULATED_AUTH_CODE.equals(code)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", SIMULATED_ACCESS_TOKEN);
        response.put("token_type", "Bearer");
        response.put("expires_in", 3600);
        response.put("scope", "launch/patient patient/Patient.read patient/Encounter.read patient/MedicationRequest.read");
        
        // Contextual Patient ID (Margaret Chen, the first patient seeded in DataSeeder)
        response.put("patient", "1");
        response.put("need_patient_banner", true);
        response.put("smart_style_url", "http://sandbox.local/smart/style");

        return ResponseEntity.ok(response);
    }
}
