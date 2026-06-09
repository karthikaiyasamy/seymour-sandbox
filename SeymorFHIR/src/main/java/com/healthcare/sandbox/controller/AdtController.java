package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.AdtEvent;
import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.AdtEventRepository;
import com.healthcare.sandbox.repository.PatientRepository;
import com.healthcare.sandbox.service.Hl7Service;
import com.healthcare.sandbox.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/fhir/Encounter/adt")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdtController {

    private final AdtEventRepository adtRepo;
    private final PatientRepository patientRepo;
    private final Hl7Service hl7Service;
    private final WebhookService webhookService;
    private final HttpServletRequest request;

    // GET /api/fhir/Encounter/adt — all ADT events
    @GetMapping
    public Map<String, Object> getAllAdtEvents() {
        List<AdtEvent> events = adtRepo.findAll();
        return buildBundle(events.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Encounter/adt/patient/{patientId}
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<Map<String, Object>> getAdtByPatient(@PathVariable Long patientId) {
        if (!patientRepo.existsById(patientId)) {
            return ResponseEntity.status(404).body(buildOperationOutcome(
                    "error", "not-found", "Patient with ID " + patientId + " not found"));
        }
        List<AdtEvent> events = adtRepo.findByPatientIdOrderByEventDatetimeDesc(patientId);
        return ResponseEntity.ok(buildBundle(events.stream().map(this::toFhir).toList()));
    }

    // GET /api/fhir/Encounter/adt/visit/{visitNumber}
    @GetMapping("/visit/{visitNumber}")
    public Map<String, Object> getAdtByVisit(@PathVariable String visitNumber) {
        List<AdtEvent> events = adtRepo.findByVisitNumberOrderByEventDatetimeAsc(visitNumber);
        return buildBundle(events.stream().map(this::toFhir).toList());
    }

    // GET /api/fhir/Encounter/adt/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAdtEvent(@PathVariable Long id) {
        return adtRepo.findById(id)
                .map(e -> ResponseEntity.ok(toFhir(e)))
                .orElse(ResponseEntity.status(404).body(buildOperationOutcome(
                        "error", "not-found", "ADT Event with ID " + id + " not found")));
    }

    // GET /api/fhir/Encounter/adt/{id}/hl7 — get HL7 v2 representation of ADT event
    @GetMapping("/{id}/hl7")
    public ResponseEntity<String> getAdtEventHl7(@PathVariable Long id) {
        return adtRepo.findById(id)
                .map(e -> ResponseEntity.ok()
                        .header("Content-Type", "text/plain")
                        .body(hl7Service.generateHl7(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/fhir/Encounter/adt/hl7 — ingest raw pipe-delimited HL7 message
    @PostMapping(value = "/hl7", consumes = "text/plain")
    public ResponseEntity<Map<String, Object>> parseAndCreateAdtEvent(@RequestBody String hl7Text) {
        try {
            AdtEvent event = hl7Service.parseHl7(hl7Text);
            AdtEvent saved = adtRepo.save(event);
            return ResponseEntity.status(201).body(toFhir(saved));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(buildOperationOutcome(
                    "error", "structure", "Failed to parse HL7 message: " + ex.getMessage()));
        }
    }

    // POST /api/fhir/Encounter/adt/{patientId} — create ADT event
    @PostMapping("/{patientId}")
    public ResponseEntity<Map<String, Object>> createAdtEvent(
            @PathVariable Long patientId,
            @RequestBody AdtEvent event) {

        return patientRepo.findById(patientId).map(patient -> {
            event.setPatient(patient);
            if (event.getEventDatetime() == null) event.setEventDatetime(LocalDateTime.now());
            AdtEvent saved = adtRepo.save(event);
            
            // Extract webhook url from header or request param
            String webhookUrl = request.getHeader("X-Webhook-Url");
            if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
                webhookUrl = request.getParameter("webhookUrl");
            }
            if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                webhookService.fireAdtWebhook(saved, patient, webhookUrl);
            }
            
            return ResponseEntity.status(201).body(toFhir(saved));
        }).orElse(ResponseEntity.status(404).body(buildOperationOutcome(
                "error", "not-found", "Patient with ID " + patientId + " not found to link ADT event")));
    }

    // ── FHIR Encounter (ADT) Mapping ──
    private Map<String, Object> toFhir(AdtEvent e) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("resourceType", "Encounter");
        resource.put("id", String.valueOf(e.getId()));
        resource.put("status", mapStatus(e.getEventType()));

        resource.put("class", Map.of(
                "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "code", mapClass(e.getPatientClass()),
                "display", e.getPatientClass() != null ? e.getPatientClass() : "UNKNOWN"
        ));

        resource.put("type", List.of(Map.of(
                "coding", List.of(Map.of(
                        "system", "http://snomed.info/sct",
                        "code", mapEventCode(e.getEventType()),
                        "display", e.getEventType()
                ))
        )));

        if (e.getPatient() != null) {
            resource.put("subject", Map.of(
                    "reference", "Patient/" + e.getPatient().getId(),
                    "display", e.getPatient().getFirstName() + " " + e.getPatient().getLastName()
            ));
        }

        resource.put("period", Map.of(
                "start", e.getEventDatetime() != null ? e.getEventDatetime().toString() : ""
        ));

        // Location (FHIR Encounter location array references)
        if (e.getFacility() != null || e.getWard() != null || e.getRoom() != null || e.getBed() != null) {
            String facility = e.getFacility() != null ? e.getFacility() : "";
            String ward = e.getWard() != null ? e.getWard() : "";
            String room = e.getRoom() != null ? "Room " + e.getRoom() : "";
            String bed = e.getBed() != null ? "Bed " + e.getBed() : "";

            StringBuilder displayBuilder = new StringBuilder(facility);
            if (!ward.isEmpty()) {
                if (displayBuilder.length() > 0) displayBuilder.append(" - ");
                displayBuilder.append(ward);
            }
            if (!room.isEmpty()) {
                if (displayBuilder.length() > 0) displayBuilder.append(", ");
                displayBuilder.append(room);
            }
            if (!bed.isEmpty()) {
                if (displayBuilder.length() > 0) displayBuilder.append(", ");
                displayBuilder.append(bed);
            }

            Map<String, Object> locObj = new LinkedHashMap<>();
            locObj.put("location", Map.of(
                    "reference", "Location/" + (e.getId() != null ? e.getId() : 1),
                    "display", displayBuilder.toString()
            ));
            resource.put("location", List.of(locObj));
        }

        if (e.getAttendingPhysician() != null) {
            resource.put("participant", List.of(Map.of(
                    "type", List.of(Map.of("text", "attending")),
                    "individual", Map.of("display", e.getAttendingPhysician())
            )));
        }

        // HL7 v2 ADT metadata
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("visitNumber", e.getVisitNumber());
        ext.put("hl7EventCode", e.getEventCode());
        if (e.getAdmittingDiagnosis() != null) ext.put("admittingDiagnosis", e.getAdmittingDiagnosis());
        if (e.getDischargeDisposition() != null) ext.put("dischargeDisposition", e.getDischargeDisposition());
        if (e.getNotes() != null) ext.put("notes", e.getNotes());
        resource.put("extension", ext);

        return resource;
    }

    private String mapStatus(String eventType) {
        if (eventType == null) return "unknown";
        return switch (eventType) {
            case "ADMIT" -> "in-progress";
            case "DISCHARGE" -> "finished";
            case "TRANSFER" -> "in-progress";
            case "REGISTER" -> "arrived";
            default -> "unknown";
        };
    }

    private String mapClass(String patientClass) {
        if (patientClass == null) return "AMB";
        return switch (patientClass) {
            case "INPATIENT" -> "IMP";
            case "OUTPATIENT" -> "AMB";
            case "EMERGENCY" -> "EMER";
            case "OBSERVATION" -> "OBSENC";
            default -> "AMB";
        };
    }

    private String mapEventCode(String eventType) {
        if (eventType == null) return "11429006";
        return switch (eventType) {
            case "ADMIT" -> "32485007";
            case "DISCHARGE" -> "58000006";
            case "TRANSFER" -> "107724000";
            default -> "11429006";
        };
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
