package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/Patient")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PatientController {

    private final PatientRepository patientRepo;

    // GET /api/fhir/Patient — list all active patients
    @GetMapping
    public Map<String, Object> getAllPatients() {
        List<Patient> patients = patientRepo.findByActiveTrue();
        return buildBundle("searchset", patients.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Patient/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPatient(@PathVariable Long id) {
        return patientRepo.findById(id)
                .map(p -> ResponseEntity.ok(toFhir(p)))
                .orElse(ResponseEntity.status(404).body(buildOperationOutcome(
                        "error", "not-found", "Patient with ID " + id + " not found")));
    }

    // GET /api/fhir/Patient?name=chen
    @GetMapping(params = "name")
    public Map<String, Object> searchByName(@RequestParam String name) {
        List<Patient> patients = patientRepo.searchByName(name);
        return buildBundle("searchset", patients.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Patient?mrn=MRN-10001
    @GetMapping(params = "mrn")
    public Map<String, Object> searchByMrn(@RequestParam String mrn) {
        return patientRepo.findByMrn(mrn)
                .map(p -> buildBundle("searchset", List.of(toFhir(p))))
                .orElse(buildBundle("searchset", List.of()));
    }

    // POST /api/fhir/Patient — create new patient
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPatient(@RequestBody Patient patient) {
        if (patientRepo.existsByMrn(patient.getMrn())) {
            return ResponseEntity.badRequest().body(buildOperationOutcome(
                    "error", "duplicate", "Patient with MRN " + patient.getMrn() + " already exists"));
        }
        Patient saved = patientRepo.save(patient);
        return ResponseEntity.status(201).body(toFhir(saved));
    }

    // PUT /api/fhir/Patient/{id} — update patient
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePatient(@PathVariable Long id, @RequestBody Patient updated) {
        return patientRepo.findById(id).map(existing -> {
            updated.setId(id);
            updated.setCreatedAt(existing.getCreatedAt());
            Patient saved = patientRepo.save(updated);
            return ResponseEntity.ok(toFhir(saved));
        }).orElse(ResponseEntity.status(404).body(buildOperationOutcome(
                "error", "not-found", "Patient with ID " + id + " not found to update")));
    }

    // DELETE /api/fhir/Patient/{id} — soft delete (set active = false)
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deactivatePatient(@PathVariable Long id) {
        return patientRepo.findById(id).map(p -> {
            p.setActive(false);
            patientRepo.save(p);
            return ResponseEntity.noContent().<Map<String, Object>>build();
        }).orElse(ResponseEntity.status(404).body(buildOperationOutcome(
                "error", "not-found", "Patient with ID " + id + " not found to deactivate")));
    }

    // ── FHIR R4 Patient Resource Mapping ──
    private Map<String, Object> toFhir(Patient p) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Patient");
        resource.put("id", String.valueOf(p.getId()));

        // Identifiers (BC CA-Baseline aligned)
        resource.put("identifier", List.of(
                Map.of("system", "urn:oid:2.16.840.1.113883.4.1", "value", p.getMrn(), "use", "usual"),
                Map.of("system", "http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn", "value",
                        p.getHealthCardNumber() != null ? p.getHealthCardNumber() : "", "use", "official")
        ));

        resource.put("active", p.getActive());

        // Name
        resource.put("name", List.of(Map.of(
                "use", "official",
                "family", p.getLastName(),
                "given", List.of(p.getFirstName())
        )));

        // Telecom
        List<Map<String, String>> telecom = new ArrayList<>();
        if (p.getPhone() != null) telecom.add(Map.of("system", "phone", "value", p.getPhone(), "use", "home"));
        if (p.getEmail() != null) telecom.add(Map.of("system", "email", "value", p.getEmail()));
        resource.put("telecom", telecom);

        resource.put("gender", p.getGender());
        resource.put("birthDate", p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : null);

        // Address
        resource.put("address", List.of(Map.of(
                "use", "home",
                "line", List.of(p.getAddressLine() != null ? p.getAddressLine() : ""),
                "city", p.getCity() != null ? p.getCity() : "",
                "state", p.getProvince() != null ? p.getProvince() : "",
                "postalCode", p.getPostalCode() != null ? p.getPostalCode() : "",
                "country", "CA"
        )));

        // Communication
        if (p.getPrimaryLanguage() != null) {
            resource.put("communication", List.of(Map.of(
                    "language", Map.of("text", p.getPrimaryLanguage()),
                    "preferred", true
            )));
        }

        // Extensions (Seymour-style extras)
        resource.put("extension", List.of(
                Map.of("url", "http://sandbox.local/fhir/StructureDefinition/bloodType",
                        "valueString", p.getBloodType() != null ? p.getBloodType() : "Unknown"),
                Map.of("url", "http://sandbox.local/fhir/StructureDefinition/allergies",
                        "valueString", p.getAllergies() != null ? p.getAllergies() : "NKDA")
        ));

        return resource;
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

    private Map<String, Object> buildBundle(String type, List<Map<String, Object>> entries) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", type);
        bundle.put("total", entries.size());
        bundle.put("timestamp", LocalDateTime.now().toString());
        bundle.put("entry", entries.stream().map(r -> Map.of("resource", r)).toList());
        return bundle;
    }
}
