package com.healthcare.sandbox.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@Slf4j
public class SmartConfigurationController {

    /**
     * Standard SMART on FHIR Discovery Metadata Endpoint.
     * GET /.well-known/smart-configuration
     * Ref: http://hl7.org/fhir/smart-app-launch/conformance.html
     */
    @GetMapping("/.well-known/smart-configuration")
    public Map<String, Object> getSmartConfiguration() {
        log.info("Serving SMART-on-FHIR Well-Known Configuration Metadata");

        Map<String, Object> smartConfig = new LinkedHashMap<>();
        smartConfig.put("authorization_endpoint", "http://localhost:8090/oauth/authorize");
        smartConfig.put("token_endpoint", "http://localhost:8090/oauth/token");
        smartConfig.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post", "private_key_jwt"));
        smartConfig.put("registration_endpoint", "http://localhost:8090/oauth/register");
        
        smartConfig.put("scopes_supported", List.of(
                "openid", "profile", "fhirUser",
                "launch", "launch/patient",
                "patient/*.read", "patient/Patient.read", "patient/Observation.read",
                "user/*.read", "user/*.write"
        ));
        
        smartConfig.put("response_types_supported", List.of("code", "token"));
        smartConfig.put("grant_types_supported", List.of("authorization_code", "client_credentials", "refresh_token"));
        
        smartConfig.put("capabilities", List.of(
                "launch-standalone",
                "launch-ehr",
                "client-public",
                "client-confidential-symmetric",
                "context-passthrough-patient",
                "permission-patient",
                "permission-user"
        ));

        smartConfig.put("code_challenge_methods_supported", List.of("S256"));
        smartConfig.put("issuer", "http://localhost:8090");

        return smartConfig;
    }
}
