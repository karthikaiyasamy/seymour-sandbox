package com.terryfox.hospital.service;

import com.terryfox.hospital.model.PatientEntity;
import com.terryfox.hospital.repository.PatientRepository;
import com.terryfox.hospital.util.PhnValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-Hospital Regional Health Interoperability Sync Service.
 * Synchronizes synthetic patient records across local sandbox nodes:
 * 1. Seymour Central EHR (Java / Spring Boot - Port 8090)
 * 2. Langley General Gateway (C# / .NET 10 - Port 8083)
 */
@Service
public class RegionalSyncService {

    private static final Logger log = LoggerFactory.getLogger(RegionalSyncService.class);

    @Value("${seymour.fhir.url:http://localhost:8090}")
    private String seymourFhirBaseUrl;

    @Value("${langley.gateway.url:http://localhost:8083}")
    private String langleyGatewayBaseUrl;

    @Autowired
    private PatientRepository patientRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Fetches a valid SMART-on-FHIR OAuth2 Bearer token from Seymour EHR.
     */
    private String fetchSeymourOAuthToken() {
        try {
            String tokenUrl = seymourFhirBaseUrl + "/oauth/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> entity = new HttpEntity<>("grant_type=authorization_code&code=SMART_AUTH_SYNC", headers);
            ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
        } catch (Exception e) {
            log.warn("[REGIONAL-SYNC] Failed to fetch OAuth2 token from Seymour EHR at {}: {}", seymourFhirBaseUrl, e.getMessage());
        }
        return null;
    }

    public Map<String, Object> synchronizeRegionalNodes() {
        log.info("[REGIONAL-SYNC] Initiating multi-hospital regional sync across BC sandbox nodes...");

        List<PatientEntity> patients = patientRepository.findAll();
        int successCountSeymour = 0;
        int successCountLangley = 0;

        // Fetch valid OAuth2 Bearer Token before syncing to Seymour EHR
        String bearerToken = fetchSeymourOAuthToken();

        String seymourPatientEndpoint = seymourFhirBaseUrl + "/api/fhir/Patient";
        String langleySyncEndpoint = langleyGatewayBaseUrl + "/api/langleygeneral/sync";

        for (PatientEntity patient : patients) {
            String maskedPhn = PhnValidator.maskPhn(patient.getPhn());

            // 1. Dispatch JSON payload to Seymour Central EHR (Port 8090) with Bearer token
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (bearerToken != null) {
                    headers.setBearerAuth(bearerToken);
                }

                Map<String, Object> seymourPatient = new HashMap<>();
                seymourPatient.put("mrn", patient.getMrn());
                seymourPatient.put("healthCardNumber", patient.getPhn());
                seymourPatient.put("firstName", patient.getGivenName());
                seymourPatient.put("lastName", patient.getFamilyName());
                seymourPatient.put("gender", patient.getGender());
                seymourPatient.put("dateOfBirth", patient.getBirthDate() != null ? patient.getBirthDate().toString() : "1980-01-01");
                seymourPatient.put("addressLine", patient.getAddressLine());
                seymourPatient.put("city", patient.getCity());
                seymourPatient.put("province", "BC");
                seymourPatient.put("postalCode", patient.getPostalCode());
                seymourPatient.put("phone", patient.getPhone());
                seymourPatient.put("active", true);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(seymourPatient, headers);
                ResponseEntity<String> response = restTemplate.exchange(seymourPatientEndpoint, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    successCountSeymour++;
                    log.info("[REGIONAL-SYNC] Synced Patient PHN {} to Seymour Central EHR", maskedPhn);
                }
            } catch (Exception ex) {
                log.warn("[REGIONAL-SYNC] Seymour Central EHR response for PHN {}: {}", maskedPhn, ex.getMessage());
            }

            // 2. Dispatch Sync DTO payload to Langley General Gateway (C# - Port 8083)
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> langleyDto = new HashMap<>();
                langleyDto.put("phn", patient.getPhn());
                langleyDto.put("mrn", patient.getMrn());
                langleyDto.put("firstName", patient.getGivenName());
                langleyDto.put("lastName", patient.getFamilyName());
                langleyDto.put("gender", patient.getGender());
                langleyDto.put("dateOfBirth", patient.getBirthDate() != null ? patient.getBirthDate().toString() : "1980-01-01");
                langleyDto.put("addressLine", patient.getAddressLine());
                langleyDto.put("city", patient.getCity());
                langleyDto.put("province", "BC");
                langleyDto.put("postalCode", patient.getPostalCode());

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(langleyDto, headers);
                ResponseEntity<String> response = restTemplate.exchange(langleySyncEndpoint, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    successCountLangley++;
                    log.info("[REGIONAL-SYNC] Synced Patient PHN {} to Langley General Gateway", maskedPhn);
                }
            } catch (Exception ex) {
                log.warn("[REGIONAL-SYNC] Langley General Gateway response for PHN {}: {}", maskedPhn, ex.getMessage());
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("status", "COMPLETED");
        summary.put("totalPatientsProcessed", patients.size());
        summary.put("seymourCentralSyncCount", successCountSeymour);
        summary.put("langleyGatewaySyncCount", successCountLangley);
        summary.put("disclaimer", "All data processed is 100% synthetic for educational and developer sandbox purposes.");

        log.info("[REGIONAL-SYNC] Regional sync pipeline complete. Processed {} patients.", patients.size());
        return summary;
    }
}
