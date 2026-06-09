package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Encounter;
import com.healthcare.sandbox.repository.EncounterRepository;
import com.healthcare.sandbox.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/DocumentReference")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EncounterController {

    private final EncounterRepository encRepo;
    private final PatientRepository patientRepo;

    // GET /api/fhir/DocumentReference/patient/{patientId}
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Map<String, Object>> getNotesByPatient(@PathVariable Long patientId) {
        if (!patientRepo.existsById(patientId)) return ResponseEntity.notFound().build();
        List<Encounter> notes = encRepo.findByPatientIdOrderByEncounterDatetimeDesc(patientId);
        return ResponseEntity.ok(buildBundle(notes.stream().map(this::toFhir).toList()));
    }

    // GET /api/fhir/DocumentReference/visit/{visitNumber}
    @GetMapping("/visit/{visitNumber}")
    public Map<String, Object> getNotesByVisit(@PathVariable String visitNumber) {
        List<Encounter> notes = encRepo.findByVisitNumberOrderByEncounterDatetimeAsc(visitNumber);
        return buildBundle(notes.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/DocumentReference/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getNote(@PathVariable Long id) {
        return encRepo.findById(id)
                .map(e -> ResponseEntity.ok(toFhir(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/fhir/DocumentReference/{patientId}
    @PostMapping("/{patientId}")
    public ResponseEntity<Map<String, Object>> createNote(
            @PathVariable Long patientId,
            @RequestBody Encounter encounter) {
        return patientRepo.findById(patientId).map(patient -> {
            encounter.setPatient(patient);
            if (encounter.getEncounterDatetime() == null) encounter.setEncounterDatetime(LocalDateTime.now());
            if (encounter.getStatus() == null) encounter.setStatus("FINAL");
            Encounter saved = encRepo.save(encounter);
            return ResponseEntity.status(201).body(toFhir(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── FHIR DocumentReference / Observation Mapping ──
    private Map<String, Object> toFhir(Encounter e) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "DocumentReference");
        resource.put("id", String.valueOf(e.getId()));
        resource.put("status", e.getStatus() != null ? e.getStatus().toLowerCase() : "final");
        resource.put("type", Map.of("text", e.getEncounterType() != null ? e.getEncounterType() : "Note"));

        if (e.getPatient() != null) {
            resource.put("subject", Map.of(
                    "reference", "Patient/" + e.getPatient().getId(),
                    "display", e.getPatient().getFirstName() + " " + e.getPatient().getLastName()
            ));
        }

        resource.put("date", e.getEncounterDatetime() != null ? e.getEncounterDatetime().toString() : null);

        if (e.getProviderName() != null) {
            resource.put("author", List.of(Map.of(
                    "display", e.getProviderName() + (e.getProviderRole() != null ? " (" + e.getProviderRole() + ")" : "")
            )));
        }

        // SOAP Content
        Map<String, Object> content = new LinkedHashMap<>();
        if (e.getChiefComplaint() != null) content.put("chiefComplaint", e.getChiefComplaint());
        if (e.getSubjective() != null) content.put("S", e.getSubjective());
        if (e.getObjective() != null) content.put("O", e.getObjective());
        if (e.getAssessment() != null) content.put("A", e.getAssessment());
        if (e.getPlan() != null) content.put("P", e.getPlan());
        resource.put("content", content);

        // Diagnosis
        if (e.getDiagnosisCode() != null) {
            resource.put("context", Map.of(
                    "encounter", List.of(Map.of("reference", "Encounter/" + e.getVisitNumber())),
                    "period", Map.of("start", e.getEncounterDatetime() != null ? e.getEncounterDatetime().toString() : ""),
                    "diagnosis", List.of(Map.of(
                            "condition", Map.of(
                                    "coding", List.of(Map.of(
                                            "system", "http://hl7.org/fhir/sid/icd-10",
                                            "code", e.getDiagnosisCode(),
                                            "display", e.getDiagnosisDescription() != null ? e.getDiagnosisDescription() : ""
                                    ))
                            )
                    ))
            ));
        }

        // Vitals
        if (e.getVitalsBp() != null) {
            Map<String, Object> vitals = new LinkedHashMap<>();
            vitals.put("bloodPressure", e.getVitalsBp());
            if (e.getVitalsHr() != null) vitals.put("heartRate", e.getVitalsHr() + " bpm");
            if (e.getVitalsTemp() != null) vitals.put("temperature", e.getVitalsTemp() + "°C");
            if (e.getVitalsSpo2() != null) vitals.put("oxygenSaturation", e.getVitalsSpo2() + "%");
            if (e.getVitalsWeightKg() != null) vitals.put("weight", e.getVitalsWeightKg() + " kg");
            resource.put("vitals", vitals);
        }

        return resource;
    }

    private Map<String, Object> buildBundle(List<Map<String, Object>> entries) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "searchset");
        bundle.put("total", entries.size());
        bundle.put("timestamp", LocalDateTime.now().toString());
        bundle.put("entry", entries.stream().map(r -> Map.of("resource", r)).toList());
        return bundle;
    }
}
