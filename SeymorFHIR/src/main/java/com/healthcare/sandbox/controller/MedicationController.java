package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Medication;
import com.healthcare.sandbox.repository.MedicationRepository;
import com.healthcare.sandbox.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/MedicationRequest")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MedicationController {

    private final MedicationRepository medRepo;
    private final PatientRepository patientRepo;

    // GET /api/fhir/MedicationRequest/patient/{patientId}
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Map<String, Object>> getMedsByPatient(@PathVariable Long patientId) {
        if (!patientRepo.existsById(patientId)) return ResponseEntity.notFound().build();
        List<Medication> meds = medRepo.findByPatientIdOrderByStartDateDesc(patientId);
        return ResponseEntity.ok(buildBundle(meds.stream().map(this::toFhir).toList()));
    }

    // GET /api/fhir/MedicationRequest/patient/{patientId}/active
    @GetMapping("/patient/{patientId}/active")
    public ResponseEntity<Map<String, Object>> getActiveMeds(@PathVariable Long patientId) {
        if (!patientRepo.existsById(patientId)) return ResponseEntity.notFound().build();
        List<Medication> meds = medRepo.findByPatientIdAndStatus(patientId, "ACTIVE");
        return ResponseEntity.ok(buildBundle(meds.stream().map(this::toFhir).toList()));
    }

    // GET /api/fhir/MedicationRequest/visit/{visitNumber}
    @GetMapping("/visit/{visitNumber}")
    public Map<String, Object> getMedsByVisit(@PathVariable String visitNumber) {
        List<Medication> meds = medRepo.findByVisitNumber(visitNumber);
        return buildBundle(meds.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/MedicationRequest/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMed(@PathVariable Long id) {
        return medRepo.findById(id)
                .map(m -> ResponseEntity.ok(toFhir(m)))
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/fhir/MedicationRequest/{patientId}
    @PostMapping("/{patientId}")
    public ResponseEntity<Map<String, Object>> createMed(
            @PathVariable Long patientId,
            @RequestBody Medication med) {
        return patientRepo.findById(patientId).map(patient -> {
            med.setPatient(patient);
            if (med.getStatus() == null) med.setStatus("ACTIVE");
            Medication saved = medRepo.save(med);
            return ResponseEntity.status(201).body(toFhir(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // PUT /api/fhir/MedicationRequest/{id}/status — update status (STOP, HOLD, etc.)
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return medRepo.findById(id).map(med -> {
            med.setStatus(status.toUpperCase());
            Medication saved = medRepo.save(med);
            return ResponseEntity.ok(toFhir(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── FHIR R4 MedicationRequest Mapping ──
    private Map<String, Object> toFhir(Medication m) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "MedicationRequest");
        resource.put("id", String.valueOf(m.getId()));
        resource.put("status", m.getStatus() != null ? m.getStatus().toLowerCase() : "unknown");
        resource.put("intent", "order");

        // Medication reference
        Map<String, Object> medConcept = new LinkedHashMap<>();
        medConcept.put("text", m.getMedicationName());
        if (m.getRxnormCode() != null) {
            medConcept.put("coding", List.of(Map.of(
                    "system", "http://www.nlm.nih.gov/research/umls/rxnorm",
                    "code", m.getRxnormCode(),
                    "display", m.getGenericName() != null ? m.getGenericName() : m.getMedicationName()
            )));
        }
        resource.put("medicationCodeableConcept", medConcept);

        if (m.getPatient() != null) {
            resource.put("subject", Map.of(
                    "reference", "Patient/" + m.getPatient().getId(),
                    "display", m.getPatient().getFirstName() + " " + m.getPatient().getLastName()
            ));
        }

        if (m.getPrescriber() != null) {
            resource.put("requester", Map.of("display", m.getPrescriber()));
        }

        if (m.getStartDate() != null) {
            resource.put("authoredOn", m.getStartDate().toString());
        }

        // Dosage
        Map<String, Object> dosage = new LinkedHashMap<>();
        if (m.getDose() != null) dosage.put("doseAndRate", List.of(
                Map.of("doseQuantity", Map.of("value", m.getDose()))
        ));
        if (m.getFrequency() != null) dosage.put("timing", Map.of("code", Map.of("text", m.getFrequency())));
        if (m.getRoute() != null) dosage.put("route", Map.of("text", m.getRoute()));
        if (m.getSpecialInstructions() != null) dosage.put("patientInstruction", m.getSpecialInstructions());
        resource.put("dosageInstruction", List.of(dosage));

        // Extensions
        Map<String, Object> ext = new LinkedHashMap<>();
        if (m.getIndication() != null) ext.put("indication", m.getIndication());
        if (m.getPharmacy() != null) ext.put("dispensingPharmacy", m.getPharmacy());
        if (m.getRefillsRemaining() != null) ext.put("refillsRemaining", m.getRefillsRemaining());
        if (m.getEndDate() != null) ext.put("endDate", m.getEndDate().toString());
        if (m.getVisitNumber() != null) ext.put("visitNumber", m.getVisitNumber());
        resource.put("extension", ext);

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
