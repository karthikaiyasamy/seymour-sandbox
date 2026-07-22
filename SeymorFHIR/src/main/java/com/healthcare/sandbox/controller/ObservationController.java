package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Observation;
import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.ObservationRepository;
import com.healthcare.sandbox.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/Observation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ObservationController {

    private final ObservationRepository observationRepo;
    private final PatientRepository patientRepo;

    // GET /api/fhir/Observation — list all observations
    @GetMapping
    public Map<String, Object> getAllObservations() {
        List<Observation> list = observationRepo.findAll();
        return buildBundle("searchset", list.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Observation/{id}
    @GetMapping("/{id}")
    public ResponseEntity<?> getObservationById(@PathVariable Long id) {
        return observationRepo.findById(id)
                .map(o -> ResponseEntity.ok((Object) toFhir(o)))
                .orElseGet(() -> ResponseEntity.status(404).body(buildOperationOutcome("error", "not-found", "Observation " + id + " not found")));
    }

    // GET /api/fhir/Observation/patient/{patientId}
    @GetMapping("/patient/{patientId}")
    public Map<String, Object> getObservationsByPatient(@PathVariable String patientId) {
        Long pid = resolvePatientId(patientId);
        if (pid == null) {
            return buildBundle("searchset", List.of());
        }
        List<Observation> list = observationRepo.findByPatientId(pid);
        return buildBundle("searchset", list.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Observation/patient/{patientId}/category/{category}
    @GetMapping("/patient/{patientId}/category/{category}")
    public Map<String, Object> getObservationsByCategory(@PathVariable String patientId, @PathVariable String category) {
        Long pid = resolvePatientId(patientId);
        if (pid == null) {
            return buildBundle("searchset", List.of());
        }
        List<Observation> list = observationRepo.findByPatientIdAndCategory(pid, category);
        return buildBundle("searchset", list.stream().map(this::toFhir).toList());
    }

    // POST /api/fhir/Observation/{patientId}
    @PostMapping("/{patientId}")
    public ResponseEntity<?> createObservation(@PathVariable String patientId, @RequestBody Map<String, Object> payload) {
        Long pid = resolvePatientId(patientId);
        if (pid == null) {
            return ResponseEntity.badRequest().body(buildOperationOutcome("error", "invalid", "Patient not found for ID: " + patientId));
        }

        Optional<Patient> pOpt = patientRepo.findById(pid);
        if (pOpt.isEmpty()) {
            return ResponseEntity.status(404).body(buildOperationOutcome("error", "not-found", "Patient record not found"));
        }

        String code = (String) payload.getOrDefault("code", "8867-4");
        String codeDisplay = (String) payload.getOrDefault("codeDisplay", "Heart Rate");
        String category = (String) payload.getOrDefault("category", "vital-signs");
        Double valueQuantity = payload.get("valueQuantity") != null ? Double.parseDouble(payload.get("valueQuantity").toString()) : null;
        String unit = (String) payload.getOrDefault("valueUnit", "beats/min");
        String interpretation = (String) payload.getOrDefault("interpretation", "N");

        Observation obs = Observation.builder()
                .patient(pOpt.get())
                .status((String) payload.getOrDefault("status", "final"))
                .category(category)
                .code(code)
                .codeSystem((String) payload.getOrDefault("codeSystem", "http://loinc.org"))
                .codeDisplay(codeDisplay)
                .valueQuantity(valueQuantity)
                .valueUnit(unit)
                .valueString((String) payload.get("valueString"))
                .interpretation(interpretation)
                .effectiveDateTime(LocalDateTime.now())
                .issued(LocalDateTime.now())
                .build();

        Observation saved = observationRepo.save(obs);
        log.info("Created Observation ID: {} [{}] for Patient ID: {}", saved.getId(), saved.getCodeDisplay(), pid);

        return ResponseEntity.status(201).body(toFhir(saved));
    }

    public Map<String, Object> toFhir(Observation obs) {
        Map<String, Object> fhir = new LinkedHashMap<>();
        fhir.put("resourceType", "Observation");
        fhir.put("id", String.valueOf(obs.getId()));
        fhir.put("status", obs.getStatus() != null ? obs.getStatus() : "final");

        if (obs.getCategory() != null) {
            fhir.put("category", List.of(Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code", obs.getCategory(),
                            "display", obs.getCategory().toUpperCase()
                    ))
            )));
        }

        Map<String, Object> codeMap = new LinkedHashMap<>();
        codeMap.put("coding", List.of(Map.of(
                "system", obs.getCodeSystem() != null ? obs.getCodeSystem() : "http://loinc.org",
                "code", obs.getCode(),
                "display", obs.getCodeDisplay()
        )));
        codeMap.put("text", obs.getCodeDisplay());
        fhir.put("code", codeMap);

        if (obs.getPatient() != null) {
            fhir.put("subject", Map.of(
                    "reference", "Patient/" + obs.getPatient().getId(),
                    "display", obs.getPatient().getFirstName() + " " + obs.getPatient().getLastName()
            ));
        }

        if (obs.getValueQuantity() != null) {
            fhir.put("valueQuantity", Map.of(
                    "value", obs.getValueQuantity(),
                    "unit", obs.getValueUnit() != null ? obs.getValueUnit() : "",
                    "system", "http://unitsofmeasure.org",
                    "code", obs.getValueUnit() != null ? obs.getValueUnit() : ""
            ));
        } else if (obs.getValueString() != null) {
            fhir.put("valueString", obs.getValueString());
        }

        if (obs.getInterpretation() != null) {
            fhir.put("interpretation", List.of(Map.of(
                    "coding", List.of(Map.of(
                            "system", "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
                            "code", obs.getInterpretation()
                    ))
            )));
        }

        if (obs.getEffectiveDateTime() != null) {
            fhir.put("effectiveDateTime", obs.getEffectiveDateTime().toString());
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
