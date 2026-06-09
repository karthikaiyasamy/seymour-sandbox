package com.langley.hospital.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langley.hospital.model.LangleyPatient;
import com.langley.hospital.repository.LangleyPatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.Duration;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class WebhookController {

    private final LangleyPatientRepository patientRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record WebhookPayload(
            String patientId,
            String mrn,
            String eventType,
            String eventCode,
            String visitNumber,
            String facility,
            String timestamp,
            String callbackBaseUrl
    ) {}

    // POST /adt-webhook — Receive event notifications from Seymour
    @PostMapping("/adt-webhook")
    public ResponseEntity<Map<String, Object>> handleAdtWebhook(@RequestBody WebhookPayload payload) {
        log.info("Received ADT Webhook for patientId: {}, MRN: {}, Event: {}", 
                payload.patientId(), payload.mrn(), payload.eventType());

        if (payload.patientId() == null || payload.callbackBaseUrl() == null) {
            log.warn("Invalid webhook payload received. Missing patientId or callbackBaseUrl.");
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing patientId or callbackBaseUrl"));
        }

        try {
            // 1. Call Seymour to get the full FHIR patient details
            String callbackUrl = payload.callbackBaseUrl() + "/api/fhir/Patient/" + payload.patientId();
            log.info("Fetching full patient details from Seymour at: {}", callbackUrl);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch patient details. Seymour returned code: {}", response.statusCode());
                return ResponseEntity.status(response.statusCode()).body(Map.of(
                        "status", "error", 
                        "message", "Seymour returned HTTP error " + response.statusCode()
                ));
            }

            // 2. Parse FHIR Patient resource
            String body = response.body();
            JsonNode root = objectMapper.readTree(body);

            String seymourId = root.path("id").asText();
            String mrn = payload.mrn(); // Use MRN from webhook

            // Extract Name
            String firstName = "";
            String lastName = "";
            JsonNode nameNode = root.path("name").get(0);
            if (nameNode != null) {
                lastName = nameNode.path("family").asText("");
                JsonNode givenNode = nameNode.path("given");
                if (givenNode.isArray() && givenNode.size() > 0) {
                    firstName = givenNode.get(0).asText("");
                }
            }

            // Extract DOB
            String birthDateStr = root.path("birthDate").asText(null);
            LocalDate dateOfBirth = null;
            if (birthDateStr != null && !birthDateStr.isEmpty()) {
                dateOfBirth = LocalDate.parse(birthDateStr);
            }

            // Extract Gender
            String gender = root.path("gender").asText("unknown");

            // Extract Telecom
            String phone = null;
            String email = null;
            JsonNode telecomNode = root.path("telecom");
            if (telecomNode.isArray()) {
                for (JsonNode t : telecomNode) {
                    String system = t.path("system").asText("");
                    String value = t.path("value").asText("");
                    if ("phone".equalsIgnoreCase(system)) {
                        phone = value;
                    } else if ("email".equalsIgnoreCase(system)) {
                        email = value;
                    }
                }
            }

            // Extract HealthCard
            String healthCard = null;
            JsonNode identifierNode = root.path("identifier");
            if (identifierNode.isArray()) {
                for (JsonNode ident : identifierNode) {
                    String system = ident.path("system").asText("");
                    if (system.contains("ca-bc-patient-phn") || system.contains("phn")) {
                        healthCard = ident.path("value").asText(null);
                    }
                }
            }

            // Extract Extensions (blood type and allergies)
            String bloodType = "Unknown";
            String allergies = "NKDA";
            JsonNode extensionNode = root.path("extension");
            if (extensionNode.isArray()) {
                for (JsonNode ext : extensionNode) {
                    String url = ext.path("url").asText("");
                    String value = ext.path("valueString").asText("");
                    if (url.contains("bloodType")) {
                        bloodType = value;
                    } else if (url.contains("allergies")) {
                        allergies = value;
                    }
                }
            }

            // 3. Save or Update Patient details in Langley's DB
            Optional<LangleyPatient> existing = patientRepo.findBySeymourPatientId(seymourId);
            LangleyPatient patient = existing.orElseGet(() -> new LangleyPatient());

            patient.setSeymourPatientId(seymourId);
            patient.setMrn(mrn);
            patient.setFirstName(firstName);
            patient.setLastName(lastName);
            patient.setDateOfBirth(dateOfBirth);
            patient.setGender(gender);
            patient.setPhone(phone);
            patient.setEmail(email);
            patient.setHealthCardNumber(healthCard);
            patient.setBloodType(bloodType);
            patient.setAllergies(allergies);

            // Update admission status based on eventType
            if ("ADMIT".equalsIgnoreCase(payload.eventType()) || "REGISTER".equalsIgnoreCase(payload.eventType())) {
                patient.setAdmitted(true);
            } else if ("DISCHARGE".equalsIgnoreCase(payload.eventType())) {
                patient.setAdmitted(false);
            }

            patientRepo.save(patient);
            log.info("Successfully synced patient: {} {} (MRN: {}) - Admitted: {}", 
                    firstName, lastName, mrn, patient.getAdmitted());

            return ResponseEntity.ok(Map.of(
                    "status", "success", 
                    "message", "Patient " + mrn + " synced successfully. Admitted: " + patient.getAdmitted()
            ));

        } catch (Exception e) {
            log.error("Error processing ADT webhook call: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error", 
                    "message", "Internal error: " + e.getMessage()
            ));
        }
    }

    // GET /api/patients — List all patients in Langley Hospital
    @GetMapping("/api/patients")
    public List<LangleyPatient> getPatients() {
        return patientRepo.findAll();
    }
}
