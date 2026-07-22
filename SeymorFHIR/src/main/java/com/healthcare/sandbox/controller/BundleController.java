package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.*;
import com.healthcare.sandbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class BundleController {

    private final PatientRepository patientRepo;
    private final ObservationRepository observationRepo;
    private final AllergyIntoleranceRepository allergyRepo;
    private final MedicationRepository medicationRepo;
    private final EncounterRepository encounterRepo;

    /**
     * POST /api/fhir — Process FHIR R4 Bundle (transaction or batch mode)
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> processBundle(@RequestBody Map<String, Object> body) {
        String resourceType = (String) body.get("resourceType");
        if (!"Bundle".equals(resourceType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "resourceType", "OperationOutcome",
                    "issue", List.of(Map.of(
                            "severity", "error",
                            "code", "invalid",
                            "diagnostics", "Expected resourceType 'Bundle' but received '" + resourceType + "'"
                    ))
            ));
        }

        String bundleType = (String) body.getOrDefault("type", "transaction");
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entry");
        if (entries == null || entries.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "resourceType", "OperationOutcome",
                    "issue", List.of(Map.of(
                            "severity", "warning",
                            "code", "empty",
                            "diagnostics", "Bundle contained zero entry elements."
                    ))
            ));
        }

        List<Map<String, Object>> responseEntries = new ArrayList<>();

        for (Map<String, Object> entry : entries) {
            Map<String, Object> resource = (Map<String, Object>) entry.get("resource");
            Map<String, Object> request = (Map<String, Object>) entry.get("request");

            if (resource == null) {
                continue;
            }

            String resType = (String) resource.get("resourceType");
            String method = request != null ? (String) request.get("method") : "POST";
            Map<String, Object> responseItem = new LinkedHashMap<>();

            try {
                if ("Patient".equals(resType)) {
                    Patient p = processPatientResource(resource);
                    responseItem.put("status", "201 Created");
                    responseItem.put("location", "Patient/" + p.getId());
                    responseItem.put("outcome", Map.of("resourceType", "Patient", "id", String.valueOf(p.getId())));
                } else if ("Observation".equals(resType)) {
                    Observation obs = processObservationResource(resource);
                    responseItem.put("status", "201 Created");
                    responseItem.put("location", "Observation/" + obs.getId());
                    responseItem.put("outcome", Map.of("resourceType", "Observation", "id", String.valueOf(obs.getId())));
                } else if ("AllergyIntolerance".equals(resType)) {
                    AllergyIntolerance ai = processAllergyResource(resource);
                    responseItem.put("status", "201 Created");
                    responseItem.put("location", "AllergyIntolerance/" + ai.getId());
                    responseItem.put("outcome", Map.of("resourceType", "AllergyIntolerance", "id", String.valueOf(ai.getId())));
                } else {
                    responseItem.put("status", "200 OK");
                    responseItem.put("location", resType + "/1");
                }
            } catch (Exception e) {
                log.error("Failed processing bundle entry [{}]: {}", resType, e.getMessage());
                responseItem.put("status", "400 Bad Request");
                responseItem.put("outcome", Map.of(
                        "resourceType", "OperationOutcome",
                        "issue", List.of(Map.of("severity", "error", "code", "invalid", "diagnostics", e.getMessage()))
                ));
            }

            responseEntries.add(Map.of("response", responseItem));
        }

        Map<String, Object> bundleResponse = new LinkedHashMap<>();
        bundleResponse.put("resourceType", "Bundle");
        bundleResponse.put("type", "transaction-response".equals(bundleType) ? "transaction-response" : bundleType + "-response");
        bundleResponse.put("timestamp", LocalDateTime.now().toString());
        bundleResponse.put("entry", responseEntries);

        log.info("Processed FHIR Bundle transaction with {} entries", entries.size());
        return ResponseEntity.ok(bundleResponse);
    }

    private Patient processPatientResource(Map<String, Object> res) {
        String mrn = extractIdentifier(res, "MRN");
        if (mrn == null) mrn = "MRN-BNDL-" + System.currentTimeMillis();

        Optional<Patient> existing = patientRepo.findByMrn(mrn);
        Patient patient = existing.orElseGet(Patient::new);

        patient.setMrn(mrn);

        List<Map<String, Object>> names = (List<Map<String, Object>>) res.get("name");
        if (names != null && !names.isEmpty()) {
            Map<String, Object> nameMap = names.get(0);
            patient.setLastName((String) nameMap.get("family"));
            List<String> givens = (List<String>) nameMap.get("given");
            if (givens != null && !givens.isEmpty()) {
                patient.setFirstName(givens.get(0));
            }
        }
        if (patient.getFirstName() == null) patient.setFirstName("Unknown");
        if (patient.getLastName() == null) patient.setLastName("Unknown");

        if (res.containsKey("gender")) {
            patient.setGender((String) res.get("gender"));
        }
        if (res.containsKey("birthDate")) {
            try {
                patient.setDateOfBirth(LocalDate.parse((String) res.get("birthDate")));
            } catch (Exception ignored) {}
        }

        String phn = extractIdentifier(res, "ca-bc-patient-phn");
        if (phn != null) patient.setHealthCardNumber(phn);

        patient.setActive(true);
        return patientRepo.save(patient);
    }

    private Observation processObservationResource(Map<String, Object> res) {
        Patient patient = resolvePatientReference(res, "subject");
        String code = "UNKNOWN";
        String display = "Observation";

        Map<String, Object> codeMap = (Map<String, Object>) res.get("code");
        if (codeMap != null) {
            display = (String) codeMap.getOrDefault("text", "Observation");
            List<Map<String, Object>> codings = (List<Map<String, Object>>) codeMap.get("coding");
            if (codings != null && !codings.isEmpty()) {
                code = (String) codings.get(0).getOrDefault("code", "UNKNOWN");
                if (codings.get(0).containsKey("display")) {
                    display = (String) codings.get(0).get("display");
                }
            }
        }

        Double val = null;
        String unit = null;
        Map<String, Object> valQty = (Map<String, Object>) res.get("valueQuantity");
        if (valQty != null) {
            val = valQty.get("value") != null ? Double.parseDouble(valQty.get("value").toString()) : null;
            unit = (String) valQty.get("unit");
        }

        Observation obs = Observation.builder()
                .patient(patient)
                .status((String) res.getOrDefault("status", "final"))
                .code(code)
                .codeDisplay(display)
                .category("vital-signs")
                .valueQuantity(val)
                .valueUnit(unit)
                .valueString((String) res.get("valueString"))
                .effectiveDateTime(LocalDateTime.now())
                .issued(LocalDateTime.now())
                .build();

        return observationRepo.save(obs);
    }

    private AllergyIntolerance processAllergyResource(Map<String, Object> res) {
        Patient patient = resolvePatientReference(res, "patient");
        String display = "Allergy";
        String code = "UNKNOWN";

        Map<String, Object> codeMap = (Map<String, Object>) res.get("code");
        if (codeMap != null) {
            display = (String) codeMap.getOrDefault("text", "Allergy");
            List<Map<String, Object>> codings = (List<Map<String, Object>>) codeMap.get("coding");
            if (codings != null && !codings.isEmpty()) {
                code = (String) codings.get(0).getOrDefault("code", "UNKNOWN");
            }
        }

        AllergyIntolerance ai = AllergyIntolerance.builder()
                .patient(patient)
                .display(display)
                .code(code)
                .clinicalStatus("active")
                .verificationStatus("confirmed")
                .category("medication")
                .criticality("high")
                .recordedDate(LocalDateTime.now())
                .build();

        return allergyRepo.save(ai);
    }

    private String extractIdentifier(Map<String, Object> res, String systemKeyword) {
        List<Map<String, Object>> idents = (List<Map<String, Object>>) res.get("identifier");
        if (idents != null) {
            for (Map<String, Object> idMap : idents) {
                String system = (String) idMap.get("system");
                if (system != null && system.toLowerCase().contains(systemKeyword.toLowerCase())) {
                    return (String) idMap.get("value");
                }
            }
            if (!idents.isEmpty()) {
                return (String) idents.get(0).get("value");
            }
        }
        return null;
    }

    private Patient resolvePatientReference(Map<String, Object> res, String fieldName) {
        Map<String, Object> refMap = (Map<String, Object>) res.get(fieldName);
        if (refMap != null) {
            String ref = (String) refMap.get("reference");
            if (ref != null && ref.contains("Patient/")) {
                String idStr = ref.substring(ref.indexOf("Patient/") + 8);
                if (idStr.matches("\\d+")) {
                    return patientRepo.findById(Long.parseLong(idStr)).orElse(patientRepo.findAll().stream().findFirst().orElse(null));
                }
            }
        }
        return patientRepo.findAll().stream().findFirst().orElse(null);
    }
}
