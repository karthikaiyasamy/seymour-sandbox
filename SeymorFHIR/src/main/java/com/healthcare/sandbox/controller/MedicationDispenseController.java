package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.model.Medication;
import com.healthcare.sandbox.model.Encounter;
import com.healthcare.sandbox.repository.PatientRepository;
import com.healthcare.sandbox.repository.MedicationRepository;
import com.healthcare.sandbox.repository.EncounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/MedicationDispense")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MedicationDispenseController {

    private final PatientRepository patientRepo;
    private final MedicationRepository medRepo;
    private final EncounterRepository encounterRepo;

    // GET /api/fhir/MedicationDispense/$MedicationProfile?patient=[BC_PHN]
    @GetMapping("/$MedicationProfile")
    public ResponseEntity<Map<String, Object>> getMedicationProfile(@RequestParam("patient") String phn) {
        // Find patient by Health Card Number (PHN)
        Optional<Patient> patientOpt = Optional.empty();
        List<Patient> activePatients = patientRepo.findByActiveTrue();
        for (Patient p : activePatients) {
            String hc = p.getHealthCardNumber();
            if (hc != null) {
                String normalizedHc = hc.replaceAll("[^0-9]", "");
                String normalizedPhn = phn.replaceAll("[^0-9]", "");
                if (hc.equalsIgnoreCase(phn) || (normalizedHc.equals(normalizedPhn) && !normalizedPhn.isEmpty())) {
                    patientOpt = Optional.of(p);
                    break;
                }
            }
        }

        if (patientOpt.isEmpty()) {
            // Check if numeric and try DB ID fallback
            if (phn.matches("\\d+")) {
                try {
                    Long longId = Long.parseLong(phn);
                    patientOpt = patientRepo.findById(longId);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (patientOpt.isEmpty()) {
            return ResponseEntity.status(404).body(buildOperationOutcome(
                    "error", "not-found", "Patient with PHN/ID " + phn + " not found"));
        }

        Patient patient = patientOpt.get();
        List<Map<String, Object>> entries = new ArrayList<>();

        // 1. Map Medications to MedicationDispense resources
        List<Medication> meds = medRepo.findByPatientIdOrderByStartDateDesc(patient.getId());
        for (Medication m : meds) {
            Map<String, Object> dispense = new LinkedHashMap<>();
            dispense.put("resourceType", "MedicationDispense");
            dispense.put("id", "disp-" + m.getId());
            dispense.put("status", "completed");
            
            // Medication concept
            Map<String, Object> medConcept = new LinkedHashMap<>();
            medConcept.put("text", m.getMedicationName());
            if (m.getRxnormCode() != null) {
                medConcept.put("coding", List.of(Map.of(
                        "system", "http://www.nlm.nih.gov/research/umls/rxnorm",
                        "code", m.getRxnormCode(),
                        "display", m.getGenericName() != null ? m.getGenericName() : m.getMedicationName()
                )));
            }
            dispense.put("medicationCodeableConcept", medConcept);
            
            dispense.put("subject", Map.of(
                    "reference", "Patient/" + (patient.getHealthCardNumber() != null ? patient.getHealthCardNumber() : patient.getId()),
                    "display", patient.getFirstName() + " " + patient.getLastName()
            ));

            if (m.getStartDate() != null) {
                dispense.put("whenHandedOver", m.getStartDate().toString());
            }

            // Dosage
            Map<String, Object> dosage = new LinkedHashMap<>();
            if (m.getDose() != null) dosage.put("doseAndRate", List.of(
                    Map.of("doseQuantity", Map.of("value", m.getDose()))
            ));
            if (m.getFrequency() != null) dosage.put("timing", Map.of("code", Map.of("text", m.getFrequency())));
            if (m.getRoute() != null) dosage.put("route", Map.of("text", m.getRoute()));
            dispense.put("dosageInstruction", List.of(dosage));

            entries.add(Map.of("resource", dispense));
        }

        // 2. Map patient.allergies to AllergyIntolerance resources
        if (patient.getAllergies() != null && !patient.getAllergies().trim().isEmpty()) {
            String[] allergyList = patient.getAllergies().split(",");
            int idx = 1;
            for (String allergyName : allergyList) {
                allergyName = allergyName.trim();
                if (allergyName.isEmpty() || allergyName.equalsIgnoreCase("NKDA")) continue;

                Map<String, Object> allergy = new LinkedHashMap<>();
                allergy.put("resourceType", "AllergyIntolerance");
                allergy.put("id", "allergy-" + patient.getId() + "-" + idx++);
                allergy.put("clinicalStatus", "active");
                allergy.put("verificationStatus", "confirmed");
                allergy.put("type", "allergy");
                allergy.put("category", List.of("medication"));
                allergy.put("code", Map.of("text", allergyName));
                allergy.put("patient", Map.of(
                        "reference", "Patient/" + (patient.getHealthCardNumber() != null ? patient.getHealthCardNumber() : patient.getId()),
                        "display", patient.getFirstName() + " " + patient.getLastName()
                ));

                entries.add(Map.of("resource", allergy));
            }
        }

        // 3. Map patient Encounters to Condition resources
        List<Encounter> encounters = encounterRepo.findByPatientIdOrderByEncounterDatetimeDesc(patient.getId());
        for (Encounter enc : encounters) {
            if (enc.getDiagnosisCode() != null) {
                Map<String, Object> condition = new LinkedHashMap<>();
                condition.put("resourceType", "Condition");
                condition.put("id", "cond-" + enc.getId());
                condition.put("clinicalStatus", "active");
                condition.put("verificationStatus", "confirmed");
                condition.put("category", List.of(Map.of(
                        "coding", List.of(Map.of(
                                "system", "http://hl7.org/fhir/condition-category",
                                "code", "encounter-diagnosis",
                                "display", "Encounter Diagnosis"
                        ))
                )));
                condition.put("code", Map.of(
                        "coding", List.of(Map.of(
                                "system", "http://hl7.org/fhir/sid/icd-10",
                                "code", enc.getDiagnosisCode(),
                                "display", enc.getDiagnosisDescription() != null ? enc.getDiagnosisDescription() : ""
                        )),
                        "text", enc.getDiagnosisDescription() != null ? enc.getDiagnosisDescription() : ""
                ));
                condition.put("subject", Map.of(
                        "reference", "Patient/" + (patient.getHealthCardNumber() != null ? patient.getHealthCardNumber() : patient.getId()),
                        "display", patient.getFirstName() + " " + patient.getLastName()
                ));

                entries.add(Map.of("resource", condition));
            }
        }

        // Build Bundle response
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "searchset");
        bundle.put("total", entries.size());
        bundle.put("timestamp", LocalDateTime.now().toString());
        bundle.put("entry", entries);

        return ResponseEntity.ok(bundle);
    }

    private Map<String, Object> buildOperationOutcome(String severity, String code, String diagnostics) {
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("resourceType", "OperationOutcome");
        
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("severity", severity);
        issue.put("code", code);
        issue.put("diagnostics", diagnostics);
        
        outcome.put("issue", List.of(issue));
        return outcome;
    }
}
