package com.langley.hospital.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.langley.hospital.model.LangleyPatient;
import com.langley.hospital.model.LangleyVaccination;
import com.langley.hospital.model.LangleyLabResult;
import com.langley.hospital.model.LangleyAllergy;
import com.langley.hospital.repository.LangleyPatientRepository;
import com.langley.hospital.repository.LangleyVaccinationRepository;
import com.langley.hospital.repository.LangleyLabResultRepository;
import com.langley.hospital.repository.LangleyAllergyRepository;
import com.langley.hospital.util.PhnValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class WebhookController {

    private final LangleyPatientRepository patientRepo;
    private final LangleyVaccinationRepository vaccineRepo;
    private final LangleyLabResultRepository labRepo;
    private final LangleyAllergyRepository allergyRepo;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

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

    public record MirthNotification(
            String messageId,
            String patientMrn,
            String dataType,
            String timestamp
    ) {}

    // POST /api/langley/pediatric/sync — Direct push synchronization from Mirth
    @PostMapping("/api/langley/pediatric/sync")
    public ResponseEntity<Map<String, String>> syncPediatricData(@RequestBody Map<String, Object> payload) {
        log.info("Received direct sync notification from Mirth for MRN: {}, dataType: {}", payload.get("patientMrn"), payload.get("dataType"));
        
        try {
            String mrn = (String) payload.get("patientMrn");
            if (mrn == null || mrn.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing patientMrn"));
            }

            Optional<LangleyPatient> patientOpt = patientRepo.findByMrn(mrn);
            if (patientOpt.isEmpty()) {
                log.warn("Patient with MRN {} not found. Direct sync aborted.", mrn);
                return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Patient MRN not found"));
            }
            LangleyPatient patient = patientOpt.get();
            String dataType = (String) payload.get("dataType");

            if ("VACCINATION".equalsIgnoreCase(dataType)) {
                String vaccineCode = (String) payload.get("vaccineCode");
                String vaccineName = (String) payload.get("vaccineName");
                String adminDateStr = (String) payload.get("administrationDate");
                LocalDateTime adminDate = adminDateStr != null && !adminDateStr.isEmpty()
                        ? LocalDateTime.parse(adminDateStr) : LocalDateTime.now();
                String lotNumber = (String) payload.get("lotNumber");
                String administeredBy = (String) payload.get("administeredBy");

                LangleyVaccination vaccine = LangleyVaccination.builder()
                        .patient(patient)
                        .vaccineCode(vaccineCode)
                        .vaccineName(vaccineName)
                        .administrationDate(adminDate)
                        .lotNumber(lotNumber)
                        .administeredBy(administeredBy)
                        .build();

                vaccineRepo.save(vaccine);
                log.info("Direct Sync: Saved vaccination record: {} for Patient MRN: {}", vaccineName, mrn);

            } else if ("LAB_TEST".equalsIgnoreCase(dataType)) {
                String testCode = (String) payload.get("testCode");
                String testName = (String) payload.get("testName");
                String testDateStr = (String) payload.get("testDate");
                LocalDateTime testDate = testDateStr != null && !testDateStr.isEmpty()
                        ? LocalDateTime.parse(testDateStr) : LocalDateTime.now();
                String resultValue = (String) payload.get("resultValue");
                String unit = (String) payload.get("unit");
                String flag = (String) payload.get("flag");

                LangleyLabResult lab = LangleyLabResult.builder()
                        .patient(patient)
                        .testCode(testCode)
                        .testName(testName)
                        .testDate(testDate)
                        .resultValue(resultValue)
                        .unit(unit)
                        .flag(flag)
                        .build();

                labRepo.save(lab);
                log.info("Direct Sync: Saved lab result: {} for Patient MRN: {}", testName, mrn);
            } else {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Unknown dataType " + dataType));
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "success",
                    "message", "Pediatric record synchronized successfully"
            ));

        } catch (Exception e) {
            log.error("Error processing direct sync payload: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    // POST /api/langley/notify — Receive event notification from Mirth
    @PostMapping("/api/langley/notify")
    public ResponseEntity<Map<String, String>> handleMirthNotification(@RequestBody MirthNotification notification) {
        log.info("Received Mirth notification: messageId={}, mrn={}, type={}", 
                notification.messageId(), notification.patientMrn(), notification.dataType());

        // Schedule the 10-second deferred retrieval task
        scheduler.schedule(() -> {
            fetchAndProcessMirthPayload(notification.messageId(), notification.dataType());
        }, 10, TimeUnit.SECONDS);

        return ResponseEntity.accepted().body(Map.of(
                "status", "success",
                "message", "Notification received. Retrieval task scheduled in 10 seconds."
        ));
    }

    private void fetchAndProcessMirthPayload(String messageId, String dataType) {
        String payloadUrl = "http://localhost:8092/payload/" + messageId;
        log.info("Fetching payload from Mirth at: {}", payloadUrl);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(payloadUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch payload from Mirth. Status code: {}", response.statusCode());
                return;
            }

            String jsonStr = response.body();
            log.info("Received JSON payload response from Mirth interface engine for messageId: {}", messageId);
            JsonNode root = objectMapper.readTree(jsonStr);

            String mrn = root.path("patientMrn").asText(null);
            if (mrn == null || mrn.isEmpty()) {
                log.error("Payload for messageId {} is missing patientMrn", messageId);
                return;
            }

            Optional<LangleyPatient> patientOpt = patientRepo.findByMrn(mrn);
            if (patientOpt.isEmpty()) {
                log.warn("Patient with MRN {} not found in Langley. Cannot associate data.", mrn);
                return;
            }
            LangleyPatient patient = patientOpt.get();

            if ("VACCINATION".equalsIgnoreCase(dataType)) {
                String vaccineCode = root.path("vaccineCode").asText("");
                String vaccineName = root.path("vaccineName").asText("");
                String adminDateStr = root.path("administrationDate").asText(null);
                LocalDateTime adminDate = adminDateStr != null && !adminDateStr.isEmpty()
                        ? LocalDateTime.parse(adminDateStr) : LocalDateTime.now();
                String lotNumber = root.path("lotNumber").asText("");
                String administeredBy = root.path("administeredBy").asText("");

                LangleyVaccination vaccine = LangleyVaccination.builder()
                        .patient(patient)
                        .vaccineCode(vaccineCode)
                        .vaccineName(vaccineName)
                        .administrationDate(adminDate)
                        .lotNumber(lotNumber)
                        .administeredBy(administeredBy)
                        .build();

                vaccineRepo.save(vaccine);
                log.info("Successfully saved vaccination record: {} for Patient MRN: {}", vaccineName, mrn);

            } else if ("LAB_TEST".equalsIgnoreCase(dataType)) {
                String testCode = root.path("testCode").asText("");
                String testName = root.path("testName").asText("");
                String testDateStr = root.path("testDate").asText(null);
                LocalDateTime testDate = testDateStr != null && !testDateStr.isEmpty()
                        ? LocalDateTime.parse(testDateStr) : LocalDateTime.now();
                String resultValue = root.path("resultValue").asText("");
                String unit = root.path("unit").asText("");
                String flag = root.path("flag").asText("");

                LangleyLabResult lab = LangleyLabResult.builder()
                        .patient(patient)
                        .testCode(testCode)
                        .testName(testName)
                        .testDate(testDate)
                        .resultValue(resultValue)
                        .unit(unit)
                        .flag(flag)
                        .build();

                labRepo.save(lab);
                log.info("Successfully saved lab result record: {} for Patient MRN: {}", testName, mrn);
            } else {
                log.warn("Unknown data type received from Mirth: {}", dataType);
            }

        } catch (Exception e) {
            log.error("Error fetching or processing payload from Mirth: {}", e.getMessage(), e);
        }
    }

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

            // Normalize and Validate PHN if present
            if (healthCard != null && !healthCard.trim().isEmpty()) {
                healthCard = healthCard.replaceAll("[^0-9]", "");
                if (!PhnValidator.isValidBCOnlyPHN(healthCard)) {
                    log.warn("Webhook patient sync contains invalid PHN checksum: '{}'", PhnValidator.maskPHN(healthCard));
                }
            }

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
            log.info("Successfully synced patient: {} {} (MRN: {}) with PHN: {} - Admitted: {}", 
                    firstName, lastName, mrn, PhnValidator.maskPHN(patient.getHealthCardNumber()), patient.getAdmitted());

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

    // GET /api/patients/{id}/vaccinations — Get vaccinations for patient
    @GetMapping("/api/patients/{id}/vaccinations")
    public List<LangleyVaccination> getVaccinations(@PathVariable Long id) {
        return vaccineRepo.findByPatientId(id);
    }

    // GET /api/patients/{id}/labs — Get lab results for patient
    @GetMapping("/api/patients/{id}/labs")
    public List<LangleyLabResult> getLabResults(@PathVariable Long id) {
        return labRepo.findByPatientId(id);
    }

    // GET /api/patients/{id}/allergies — Get allergies for patient
    @GetMapping("/api/patients/{id}/allergies")
    public List<LangleyAllergy> getAllergies(@PathVariable Long id) {
        return allergyRepo.findByPatientId(id);
    }

    // POST /api/langley/pediatric/allergy-sync — Sync pediatric allergy record
    @PostMapping("/api/langley/pediatric/allergy-sync")
    public ResponseEntity<Map<String, String>> syncAllergyData(@RequestBody Map<String, Object> payload) {
        log.info("Received pediatric allergy sync notification for MRN: {}", payload.get("patientMrn"));
        try {
            String mrn = (String) payload.get("patientMrn");
            if (mrn == null || mrn.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Missing patientMrn"));
            }

            Optional<LangleyPatient> patientOpt = patientRepo.findByMrn(mrn);
            if (patientOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("status", "error", "message", "Patient MRN not found"));
            }

            LangleyAllergy allergy = LangleyAllergy.builder()
                    .patient(patientOpt.get())
                    .allergyCode((String) payload.getOrDefault("allergyCode", "UNKNOWN"))
                    .allergyDisplay((String) payload.getOrDefault("allergyDisplay", "Unspecified Allergy"))
                    .category((String) payload.getOrDefault("category", "medication"))
                    .criticality((String) payload.getOrDefault("criticality", "low"))
                    .reaction((String) payload.getOrDefault("reaction", "Rash"))
                    .syncedAt(LocalDateTime.now())
                    .build();

            allergyRepo.save(allergy);
            log.info("Saved synced allergy record: {} for Patient MRN: {}", allergy.getAllergyDisplay(), mrn);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "success",
                    "message", "Pediatric allergy record synchronized successfully"
            ));
        } catch (Exception e) {
            log.error("Error processing allergy sync payload: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
