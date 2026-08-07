package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.PatientRepository;
import com.healthcare.sandbox.util.PhnValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/Patient")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PatientController {

    private final PatientRepository patientRepo;

    // GET /api/fhir/Patient — list all active patients
    @GetMapping
    public Map<String, Object> getAllPatients() {
        List<Patient> patients = patientRepo.findByActiveTrue();
        return buildBundle("searchset", patients.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Patient/{id} — supports DB ID or BC PHN (Health Card Number)
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPatient(@PathVariable String id) {
        // Try looking up by DB ID if numeric
        if (id.matches("\\d+")) {
            try {
                Long longId = Long.parseLong(id);
                Optional<Patient> p = patientRepo.findById(longId);
                if (p.isPresent()) {
                    return ResponseEntity.ok(toFhir(p.get()));
                }
            } catch (NumberFormatException ignored) {}
        }

        // Try looking up by Health Card Number (PHN)
        List<Patient> activePatients = patientRepo.findByActiveTrue();
        for (Patient p : activePatients) {
            String hc = p.getHealthCardNumber();
            if (hc != null) {
                String normalizedHc = hc.replaceAll("[^0-9]", "");
                String normalizedId = id.replaceAll("[^0-9]", "");
                if (hc.equalsIgnoreCase(id) || (normalizedHc.equals(normalizedId) && !normalizedId.isEmpty())) {
                    return ResponseEntity.ok(toFhir(p));
                }
            }
        }

        return ResponseEntity.status(404).body(buildOperationOutcome(
                "error", "not-found", "Patient with ID/PHN " + id + " not found"));
    }

    // GET /api/fhir/Patient?_id=[BC_PHN]
    @GetMapping(params = "_id")
    public Map<String, Object> searchByPhn(@RequestParam("_id") String phn) {
        List<Patient> patients = new ArrayList<>();
        patientRepo.findByActiveTrue().forEach(p -> {
            String hc = p.getHealthCardNumber();
            if (hc != null) {
                String normalizedHc = hc.replaceAll("[^0-9]", "");
                String normalizedPhn = phn.replaceAll("[^0-9]", "");
                if (hc.equalsIgnoreCase(phn) || (normalizedHc.equals(normalizedPhn) && !normalizedPhn.isEmpty())) {
                    patients.add(p);
                }
            }
        });
        return buildBundle("searchset", patients.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Patient?identifier=[system]|[value]
    @GetMapping(params = "identifier")
    public Map<String, Object> searchByIdentifier(@RequestParam("identifier") String identifier) {
        String system = null;
        String value = identifier;
        if (identifier.contains("|")) {
            String[] parts = identifier.split("\\|", 2);
            system = parts[0];
            value = parts[1];
        }

        List<Patient> patients = new ArrayList<>();
        final String finalSystem = system;
        final String finalValue = value;

        if (finalSystem == null) {
            patientRepo.findByMrn(finalValue).ifPresent(patients::add);
            patientRepo.findByActiveTrue().forEach(p -> {
                if (finalValue.equalsIgnoreCase(p.getHealthCardNumber())) {
                    patients.add(p);
                }
            });
        } else if (finalSystem.contains("patient-phn") || finalSystem.contains("healthcare-id")) {
            patientRepo.findByActiveTrue().forEach(p -> {
                String hc = p.getHealthCardNumber();
                if (hc != null) {
                    String normalizedHc = hc.replaceAll("[^0-9]", "");
                    String normalizedVal = finalValue.replaceAll("[^0-9]", "");
                    if (hc.equalsIgnoreCase(finalValue) || (normalizedHc.equals(normalizedVal) && !normalizedVal.isEmpty())) {
                        patients.add(p);
                    }
                }
            });
        } else {
            patientRepo.findByMrn(finalValue).ifPresent(patients::add);
        }

        return buildBundle("searchset", patients.stream().map(this::toFhir).toList());
    }

    // POST /api/fhir/Patient/$match — Client Registry Services (CRS) matching operation
    @PostMapping("/$match")
    public ResponseEntity<Map<String, Object>> patientMatch(@RequestBody Map<String, Object> body) {
        Map<String, Object> patientResource = null;
        if ("Parameters".equals(body.get("resourceType"))) {
            List<Map<String, Object>> params = (List<Map<String, Object>>) body.get("parameter");
            if (params != null) {
                for (Map<String, Object> param : params) {
                    if ("patient".equals(param.get("name")) && param.containsKey("resource")) {
                        patientResource = (Map<String, Object>) param.get("resource");
                        break;
                    }
                }
            }
        } else if ("Patient".equals(body.get("resourceType"))) {
            patientResource = body;
        }

        if (patientResource == null) {
            return ResponseEntity.badRequest().body(buildOperationOutcome(
                    "error", "invalid", "Missing Patient resource or parameter in request body"));
        }

        String family = null;
        String given = null;
        String birthDate = null;

        List<Map<String, Object>> names = (List<Map<String, Object>>) patientResource.get("name");
        if (names != null && !names.isEmpty()) {
            Map<String, Object> nameMap = names.get(0);
            family = (String) nameMap.get("family");
            List<String> givens = (List<String>) nameMap.get("given");
            if (givens != null && !givens.isEmpty()) {
                given = givens.get(0);
            }
        }

        birthDate = (String) patientResource.get("birthDate");

        if (family == null || given == null || birthDate == null) {
            return ResponseEntity.badRequest().body(buildOperationOutcome(
                    "error", "business-rule", "Mandatory matching criteria missing: family, given, and birthDate are required."));
        }

        LocalDate dob;
        try {
            dob = LocalDate.parse(birthDate);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(buildOperationOutcome(
                    "error", "invalid", "Invalid birthDate format. Expected YYYY-MM-DD."));
        }

        List<Patient> allPatients = patientRepo.findByActiveTrue();
        List<Map<String, Object>> matchedEntries = new ArrayList<>();

        for (Patient p : allPatients) {
            boolean matchFamily = p.getLastName().equalsIgnoreCase(family);
            boolean matchGiven = p.getFirstName().equalsIgnoreCase(given);
            boolean matchDob = p.getDateOfBirth() != null && p.getDateOfBirth().equals(dob);

            if (matchFamily && matchGiven && matchDob) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("resource", toFhir(p));

                Map<String, Object> search = new LinkedHashMap<>();
                search.put("mode", "match");
                search.put("score", 1.0);
                entry.put("search", search);

                matchedEntries.add(entry);
            }
        }

        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("resourceType", "Bundle");
        bundle.put("type", "searchset");
        bundle.put("total", matchedEntries.size());
        bundle.put("timestamp", LocalDateTime.now().toString());
        bundle.put("entry", matchedEntries);

        return ResponseEntity.ok(bundle);
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

        // Validate BC PHN if provided
        String phn = patient.getHealthCardNumber();
        if (phn != null && !phn.trim().isEmpty()) {
            String normalizedPhn = phn.replaceAll("[^0-9]", "");
            if (!PhnValidator.isValidBCOnlyPHN(normalizedPhn)) {
                log.warn("Patient registration rejected: Invalid PHN checksum '{}'", PhnValidator.maskPHN(normalizedPhn));
                return ResponseEntity.badRequest().body(buildOperationOutcome(
                        "error", "invalid", "Invalid British Columbia PHN format or checksum."));
            }
            patient.setHealthCardNumber(normalizedPhn);
        }

        log.info("Registering new patient record with PHN: {}", 
                PhnValidator.maskPHN(patient.getHealthCardNumber()));

        Patient saved = patientRepo.save(patient);
        return ResponseEntity.status(201).body(toFhir(saved));
    }

    // PUT /api/fhir/Patient/{id} — update patient
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePatient(@PathVariable Long id, @RequestBody Patient updated) {
        // Validate BC PHN if provided
        String phn = updated.getHealthCardNumber();
        if (phn != null && !phn.trim().isEmpty()) {
            String normalizedPhn = phn.replaceAll("[^0-9]", "");
            if (!PhnValidator.isValidBCOnlyPHN(normalizedPhn)) {
                log.warn("Patient update rejected: Invalid PHN checksum '{}'", PhnValidator.maskPHN(normalizedPhn));
                return ResponseEntity.badRequest().body(buildOperationOutcome(
                        "error", "invalid", "Invalid British Columbia PHN format or checksum."));
            }
            updated.setHealthCardNumber(normalizedPhn);
        }

        return patientRepo.findById(id).map(existing -> {
            updated.setId(id);
            updated.setCreatedAt(existing.getCreatedAt());

            log.info("Updating patient ID: {}, MRN: {}, PHN: {}", 
                    id, 
                    updated.getMrn(), 
                    PhnValidator.maskPHN(updated.getHealthCardNumber()));

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
