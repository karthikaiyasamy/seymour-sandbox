package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.AllergyIntolerance;
import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.AllergyIntoleranceRepository;
import com.healthcare.sandbox.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/AllergyIntolerance")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AllergyIntoleranceController {

    private final AllergyIntoleranceRepository allergyRepo;
    private final PatientRepository patientRepo;

    // GET /api/fhir/AllergyIntolerance — list all active allergies
    @GetMapping
    public Map<String, Object> getAllAllergies() {
        List<AllergyIntolerance> list = allergyRepo.findAll();
        return buildBundle("searchset", list.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/AllergyIntolerance/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> getAllergyById(@PathVariable Long id) {
        return allergyRepo.findById(id)
                .map(a -> ResponseEntity.ok((Object) toFhir(a)))
                .orElseGet(() -> ResponseEntity.status(404).body(buildOperationOutcome("error", "not-found", "AllergyIntolerance " + id + " not found")));
    }

    // GET /api/fhir/AllergyIntolerance/patient/{patientId}
    @GetMapping("/patient/{patientId}")
    public Map<String, Object> getAllergiesByPatient(@PathVariable String patientId) {
        Long pid = resolvePatientId(patientId);
        if (pid == null) {
            return buildBundle("searchset", List.of());
        }
        List<AllergyIntolerance> list = allergyRepo.findByPatientId(pid);
        return buildBundle("searchset", list.stream().map(this::toFhir).toList());
    }

    // POST /api/fhir/AllergyIntolerance/{patientId}
    @PostMapping("/{patientId}")
    public ResponseEntity<?> createAllergy(@PathVariable String patientId, @RequestBody Map<String, Object> payload) {
        Long pid = resolvePatientId(patientId);
        if (pid == null) {
            return ResponseEntity.badRequest().body(buildOperationOutcome("error", "invalid", "Patient not found for ID: " + patientId));
        }

        Optional<Patient> pOpt = patientRepo.findById(pid);
        if (pOpt.isEmpty()) {
            return ResponseEntity.status(404).body(buildOperationOutcome("error", "not-found", "Patient record not found"));
        }

        String display = (String) payload.getOrDefault("display", "Unspecified Allergy");
        String code = (String) payload.getOrDefault("code", "UNKNOWN");
        String category = (String) payload.getOrDefault("category", "medication");
        String criticality = (String) payload.getOrDefault("criticality", "low");
        String manifestation = (String) payload.getOrDefault("reactionManifestation", "Rash");

        AllergyIntolerance ai = AllergyIntolerance.builder()
                .patient(pOpt.get())
                .display(display)
                .code(code)
                .category(category)
                .criticality(criticality)
                .clinicalStatus((String) payload.getOrDefault("clinicalStatus", "active"))
                .verificationStatus((String) payload.getOrDefault("verificationStatus", "confirmed"))
                .reactionManifestation(manifestation)
                .reactionSeverity((String) payload.getOrDefault("reactionSeverity", "moderate"))
                .recordedDate(LocalDateTime.now())
                .build();

        AllergyIntolerance saved = allergyRepo.save(ai);
        log.info("Created AllergyIntolerance ID: {} for Patient ID: {}", saved.getId(), pid);

        return ResponseEntity.status(201).body(toFhir(saved));
    }

    public Map<String, Object> toFhir(AllergyIntolerance a) {
        Map<String, Object> fhir = new LinkedHashMap<>();
        fhir.put("resourceType", "AllergyIntolerance");
        fhir.put("id", String.valueOf(a.getId()));

        Map<String, Object> clinicalStatus = new LinkedHashMap<>();
        clinicalStatus.put("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
                "code", a.getClinicalStatus() != null ? a.getClinicalStatus() : "active"
        )));
        fhir.put("clinicalStatus", clinicalStatus);

        Map<String, Object> verificationStatus = new LinkedHashMap<>();
        verificationStatus.put("coding", List.of(Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
                "code", a.getVerificationStatus() != null ? a.getVerificationStatus() : "confirmed"
        )));
        fhir.put("verificationStatus", verificationStatus);

        if (a.getCategory() != null) {
            fhir.put("category", List.of(a.getCategory()));
        }

        if (a.getCriticality() != null) {
            fhir.put("criticality", a.getCriticality());
        }

        Map<String, Object> codeMap = new LinkedHashMap<>();
        codeMap.put("text", a.getDisplay());
        codeMap.put("coding", List.of(Map.of(
                "system", "http://snomed.info/sct",
                "code", a.getCode() != null ? a.getCode() : "297422002",
                "display", a.getDisplay()
        )));
        fhir.put("code", codeMap);

        if (a.getPatient() != null) {
            fhir.put("patient", Map.of(
                    "reference", "Patient/" + a.getPatient().getId(),
                    "display", a.getPatient().getFirstName() + " " + a.getPatient().getLastName()
            ));
        }

        if (a.getReactionManifestation() != null) {
            fhir.put("reaction", List.of(Map.of(
                    "manifestation", List.of(Map.of(
                            "text", a.getReactionManifestation()
                    )),
                    "severity", a.getReactionSeverity() != null ? a.getReactionSeverity() : "moderate"
            )));
        }

        if (a.getRecordedDate() != null) {
            fhir.put("recordedDate", a.getRecordedDate().toString());
        }

        return fhir;
    }

    private Long resolvePatientId(String patientId) {
        if (patientId.matches("\\d+")) {
            return Long.parseLong(patientId);
        }
        return patientRepo.findByMrn(patientId).map(Patient::getId).orElse(null);
    }

    private Map<String, Object> buildBundle(String type, List<Map<String, Object>> entries) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", type);
        bundle.put("total", entries.size());
        bundle.put("entry", entries.stream().map(r -> Map.of("resource", r)).toList());
        return bundle;
    }

    private Map<String, Object> buildOperationOutcome(String severity, String code, String diagnostics) {
        return Map.of(
                "resourceType", "OperationOutcome",
                "issue", List.of(Map.of(
                        "severity", severity,
                        "code", code,
                        "diagnostics", diagnostics
                ))
        );
    }
}
