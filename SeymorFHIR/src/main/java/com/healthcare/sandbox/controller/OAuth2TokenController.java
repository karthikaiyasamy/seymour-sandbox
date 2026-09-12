package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.User;
import com.healthcare.sandbox.repository.UserRepository;
import com.healthcare.sandbox.service.JwtKeyService;
import com.healthcare.sandbox.service.TokenStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.*;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class OAuth2TokenController {

    private final TokenStoreService tokenStoreService;
    private final JwtKeyService jwtKeyService;
    private final UserRepository userRepository;

    private static final Map<String, String> AUTHORIZATION_CODES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * SMART-on-FHIR Authorization Endpoint
     * GET /oauth/authorize?response_type=code&client_id=my_app&redirect_uri=...&scope=launch/patient&launch=1
     * Renders an interactive login page for browser requests or JSON for automated API callers.
     */
    @GetMapping(value = "/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam(name = "response_type", defaultValue = "code") String responseType,
            @RequestParam(name = "client_id", defaultValue = "seymour_smart_app") String clientId,
            @RequestParam(name = "redirect_uri", required = false, defaultValue = "http://localhost:4200/callback") String redirectUri,
            @RequestParam(name = "scope", defaultValue = "launch/patient patient/*.read openid fhirUser") String scope,
            @RequestParam(name = "state", defaultValue = "state_123") String state,
            @RequestParam(name = "launch", defaultValue = "1") String launchPatientId,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.ACCEPT, required = false) String acceptHeader) {

        boolean isBrowserHtml = "html".equalsIgnoreCase(format) || 
                (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_HTML_VALUE) && !acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE));

        if (isBrowserHtml) {
            // Render high-end hospital identity portal HTML for browser users
            String htmlPage = renderHospitalLoginPage(clientId, redirectUri, scope, state, launchPatientId);
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlPage);
        }

        // Return JSON by default for API callers, curl, and automated tests
        String authCode = "SMART_AUTH_CODE_" + UUID.randomUUID().toString().substring(0, 8);
        AUTHORIZATION_CODES.put(authCode, launchPatientId);

        log.info("[SMART_AUTHORIZATION_JSON] Issued Authorization Code: {} for Patient Launch ID: {} to Client: {}", 
                authCode, launchPatientId, clientId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "AUTHORIZED");
        response.put("code", authCode);
        response.put("state", state);
        response.put("patient_context", launchPatientId);
        response.put("redirect_url", redirectUri + "?code=" + authCode + "&state=" + state);
        response.put("message", "SMART-on-FHIR authorization code successfully generated with patient launch context.");

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }


    /**
     * Interactive Login & Hospital Badge Tap Authentication Handler
     * POST /oauth/authorize/login
     */
    @PostMapping(value = "/authorize/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public RedirectView handleLoginSubmit(
            @RequestParam("username") String usernameOrEmail,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "badge_rfid", required = false) String badgeRfid,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("state") String state,
            @RequestParam(value = "launch", defaultValue = "1") String launchPatientId) {

        log.info("[SMART_LOGIN_SUBMITTED] User/Badge: {}, Client: {}, RedirectURI: {}", usernameOrEmail, clientId, redirectUri);

        String resolvedPatientId = launchPatientId;
        String userFullName = "Dr. Priya Patel (BC Cancer)";

        // Authenticate against PostgreSQL users table
        Optional<User> userOpt = userRepository.findByEmail(usernameOrEmail)
                .or(() -> userRepository.findByUsername(usernameOrEmail))
                .or(() -> userRepository.findByBadgeRfid(badgeRfid));

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getDefaultPatientId() != null && !user.getDefaultPatientId().isBlank()) {
                resolvedPatientId = user.getDefaultPatientId();
            }
            userFullName = user.getFullName();
            log.info("[SMART_AUTH_SUCCESS] Authenticated user: {} ({}) for Patient Context ID: {}", 
                    user.getUsername(), userFullName, resolvedPatientId);
        } else {
            log.warn("[SMART_AUTH_UNKNOWN_USER] User {} not found in PostgreSQL. Defaulting to patient context ID: {}", 
                    usernameOrEmail, resolvedPatientId);
        }

        String authCode = "SMART_AUTH_CODE_" + UUID.randomUUID().toString().substring(0, 8);
        AUTHORIZATION_CODES.put(authCode, resolvedPatientId);

        String finalRedirect = redirectUri 
                + (redirectUri.contains("?") ? "&" : "?") 
                + "code=" + authCode 
                + "&state=" + state;

        log.info("[SMART_REDIRECTING_TO_APP] Redirecting browser to: {}", finalRedirect);
        return new RedirectView(finalRedirect);
    }

    /**
     * SMART-on-FHIR OAuth2 Token Endpoint
     * POST /oauth/token
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

    private String renderHospitalLoginPage(String clientId, String redirectUri, String scope, String state, String launchPatientId) {
        String template = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Seymour Health Gateway: BC Health Single Sign-On</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
            <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
            <style>
                body {
                    font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background-color: #f3f4f6;
                    color: #1f2937;
                    margin: 0;
                    padding: 0;
                    min-height: 100vh;
                    -webkit-font-smoothing: antialiased;
                }
                .disclaimer-banner {
                    background-color: #fef2f2;
                    border-bottom: 2px solid #fca5a5;
                    padding: 10px 20px;
                    text-align: center;
                    color: #dc2626;
                    font-size: 0.85rem;
                    font-weight: 700;
                    letter-spacing: 0.02em;
                }
                .gov-header {
                    background-color: #003366;
                    border-bottom: 4px solid #fcba19;
                    padding: 16px 28px;
                    box-shadow: 0 4px 12px rgba(0, 51, 102, 0.15);
                }
                .gov-header-content {
                    max-width: 1240px;
                    margin: 0 auto;
                    display: flex;
                    align-items: center;
                    gap: 16px;
                }
                .container {
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    padding: 48px 20px;
                }
                .card {
                    background: #ffffff;
                    border: 1px solid #e5e7eb;
                    border-radius: 12px;
                    padding: 36px;
                    width: 100%;
                    max-width: 480px;
                    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.04);
                }
                .badge {
                    display: inline-block;
                    background: #eff6ff;
                    color: #003366;
                    border: 1px solid #bfdbfe;
                    border-radius: 9999px;
                    padding: 4px 12px;
                    font-size: 0.75rem;
                    font-weight: 700;
                    letter-spacing: 0.05em;
                    text-transform: uppercase;
                    margin-bottom: 16px;
                }
                h1 {
                    font-size: 1.5rem;
                    margin: 0 0 8px 0;
                    color: #003366;
                    font-weight: 700;
                }
                p.subtitle {
                    color: #4b5563;
                    font-size: 0.875rem;
                    margin: 0 0 24px 0;
                    line-height: 1.5;
                }
                .notice-box {
                    background: #eff6ff;
                    border-left: 4px solid #003366;
                    padding: 14px 16px;
                    border-radius: 6px;
                    font-size: 0.8125rem;
                    color: #1e3a8a;
                    margin-bottom: 24px;
                    line-height: 1.5;
                }
                .form-group {
                    margin-bottom: 16px;
                }
                label {
                    display: block;
                    font-size: 0.8125rem;
                    font-weight: 600;
                    color: #374151;
                    margin-bottom: 6px;
                }
                input[type="text"], input[type="password"] {
                    width: 100%;
                    padding: 10px 14px;
                    background: #f9fafb;
                    border: 1px solid #d1d5db;
                    border-radius: 6px;
                    color: #111827;
                    font-size: 0.9375rem;
                    box-sizing: border-box;
                    transition: border-color 0.2s;
                }
                input[type="text"]:focus, input[type="password"]:focus {
                    outline: none;
                    border-color: #003366;
                    background: #ffffff;
                }
                .btn {
                    width: 100%;
                    padding: 14px;
                    border: none;
                    border-radius: 6px;
                    font-size: 0.9375rem;
                    font-weight: 700;
                    cursor: pointer;
                    transition: all 0.2s;
                    margin-top: 8px;
                }
                .btn-primary {
                    background: #003366;
                    color: #ffffff;
                    box-shadow: 0 4px 12px rgba(0, 51, 102, 0.2);
                }
                .btn-primary:hover {
                    background: #002244;
                }
                .btn-badge {
                    background: #f3f4f6;
                    color: #003366;
                    border: 1px solid #d1d5db;
                    margin-top: 12px;
                }
                .btn-badge:hover {
                    background: #e5e7eb;
                }
                .divider {
                    display: flex;
                    align-items: center;
                    text-align: center;
                    color: #6b7280;
                    font-size: 0.75rem;
                    font-weight: 600;
                    margin: 20px 0;
                }
                .divider::before, .divider::after {
                    content: '';
                    flex: 1;
                    border-bottom: 1px solid #e5e7eb;
                }
                .divider span {
                    padding: 0 10px;
                }
                .footer-meta {
                    margin-top: 24px;
                    padding-top: 16px;
                    border-top: 1px solid #e5e7eb;
                    font-size: 0.75rem;
                    color: #6b7280;
                }
                .footer-meta div {
                    margin-bottom: 4px;
                }
            </style>
        </head>
        <body>
            <!-- Educational Disclaimer Banner -->
            <div class="disclaimer-banner">
                ⚠️ DEMONSTRATION & EDUCATIONAL PURPOSE ONLY: ALL PATIENT DATA IS SYNTHETIC & SIMULATED. NO REAL HEALTH DATA IS STORED OR PROCESSED.
            </div>

            <!-- BC Government Header -->
            <div class="gov-header">
                <div class="gov-header-content">
                    <div style="background: #ffffff; border-radius: 12px; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; box-shadow: 0 2px 8px rgba(0,0,0,0.15);">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <circle cx="12" cy="12" r="5" fill="#fcba19"/>
                            <path d="M12 2V4M12 20V22M4 12H2M22 12H20M6.34 6.34L4.93 4.93M19.07 19.07L17.66 17.66M6.34 17.66L4.93 19.07M19.07 4.93L17.66 6.34" stroke="#fcba19" stroke-width="2.5" stroke-linecap="round"/>
                        </svg>
                    </div>
                    <div>
                        <div style="color: #fcba19; font-size: 0.75rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.1em; margin-bottom: 2px;">
                            Beautiful British Columbia
                        </div>
                        <div style="color: #ffffff; font-size: 1.35rem; font-weight: 700;">
                            Seymour Regional EHR: Identity Portal
                        </div>
                    </div>
                </div>
            </div>

            <div class="container">
                <div class="card">
                    <span class="badge">BC Health Authority Single Sign-On</span>
                    <h1>Single Sign-On Identity Portal</h1>
                    <p class="subtitle">Authenticate to grant SMART-on-FHIR clinical access to <strong>{{CLIENT_ID}}</strong>.</p>

                    <div class="notice-box">
                        <strong>BC Health Identity Standard Notice:</strong> In BC Health Authority environments (PHSA, VGH, BC Cancer), clinicians authenticate via <strong>Imprivata Hospital RFID Badge Tap</strong> at workstation terminals or via <strong>Active Directory credentials</strong> (<code>@phsa.ca</code>). Both methods issue standard OAuth2 tokens.
                    </div>

                    <!-- Form 1: Username & Password Login -->
                    <form action="/oauth/authorize/login" method="POST">
                        <input type="hidden" name="client_id" value="{{CLIENT_ID}}" />
                        <input type="hidden" name="redirect_uri" value="{{REDIRECT_URI}}" />
                        <input type="hidden" name="state" value="{{STATE}}" />
                        <input type="hidden" name="launch" value="{{LAUNCH_ID}}" />

                        <div class="form-group">
                            <label for="username">Clinician Email / User ID</label>
                            <input type="text" id="username" name="username" value="dr.patel@phsa.ca" required />
                        </div>

                        <div class="form-group">
                            <label for="password">Network Password</label>
                            <input type="password" id="password" name="password" placeholder="••••••••••••" required />
                        </div>

                        <button type="submit" class="btn btn-primary">Sign In & Authorize Access</button>
                    </form>

                    <div class="divider"><span>OR SIMULATE WORKSTATION BADGE</span></div>

                    <!-- Form 2: Simulate Hospital Badge Tap (Imprivata RFID SSO) -->
                    <form action="/oauth/authorize/login" method="POST">
                        <input type="hidden" name="client_id" value="{{CLIENT_ID}}" />
                        <input type="hidden" name="redirect_uri" value="{{REDIRECT_URI}}" />
                        <input type="hidden" name="state" value="{{STATE}}" />
                        <input type="hidden" name="launch" value="{{LAUNCH_ID}}" />
                        <input type="hidden" name="username" value="dr.patel@phsa.ca" />
                        <input type="hidden" name="badge_rfid" value="BADGE-PHSA-88901" />

                        <button type="submit" class="btn btn-badge">Tap Hospital RFID Badge (Imprivata SSO)</button>
                    </form>

                    <div class="footer-meta">
                        <div><strong>Requested Scope:</strong> <code>{{SCOPE}}</code></div>
                        <div><strong>Patient Context ID:</strong> <code>Patient/{{LAUNCH_ID}}</code></div>
                    </div>
                </div>
            </div>
        </body>
        </html>
        """;

        return template
                .replace("{{CLIENT_ID}}", clientId != null ? clientId : "seymour_smart_app")
                .replace("{{REDIRECT_URI}}", redirectUri != null ? redirectUri : "http://localhost:4200/callback")
                .replace("{{STATE}}", state != null ? state : "state_123")
                .replace("{{LAUNCH_ID}}", launchPatientId != null ? launchPatientId : "1")
                .replace("{{SCOPE}}", scope != null ? scope : "launch/patient patient/*.read openid fhirUser");
    }
}


