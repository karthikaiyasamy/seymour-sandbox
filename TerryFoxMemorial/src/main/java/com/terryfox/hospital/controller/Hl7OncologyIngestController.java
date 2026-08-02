package com.terryfox.hospital.controller;

import com.terryfox.hospital.model.GenomicReportEntity;
import com.terryfox.hospital.repository.GenomicReportRepository;
import com.terryfox.hospital.model.PatientEntity;
import com.terryfox.hospital.repository.PatientRepository;
import com.terryfox.hospital.util.PhnValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/terryfox/hl7")
public class Hl7OncologyIngestController {

    private static final Logger log = LoggerFactory.getLogger(Hl7OncologyIngestController.class);

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private GenomicReportRepository genomicRepository;

    @PostMapping(consumes = {"text/plain", "application/x-hl7", "application/json"})
    public ResponseEntity<Map<String, Object>> ingestHl7Message(@RequestBody String rawHl7Message) {
        log.info("[TERRY-FOX-HL7] Ingesting inbound raw HL7 v2 pathology message...");

        Map<String, Object> response = new HashMap<>();
        String phn = null;
        String familyName = "Unknown";
        String givenName = "Unknown";
        String geneTarget = "GENOMIC_PANEL";
        String resultText = "Pathology Result";
        String title = "HL7 Molecular Diagnostic Report";

        String[] lines = rawHl7Message.split("\r?\n");
        for (String line : lines) {
            String[] fields = line.split("\\|");
            if (fields.length == 0) continue;

            String segment = fields[0];
            if ("PID".equalsIgnoreCase(segment)) {
                if (fields.length > 3 && !fields[3].isEmpty()) {
                    phn = fields[3].split("\\^")[0];
                }
                if (fields.length > 5 && !fields[5].isEmpty()) {
                    String[] nameParts = fields[5].split("\\^");
                    if (nameParts.length > 0) familyName = nameParts[0];
                    if (nameParts.length > 1) givenName = nameParts[1];
                }
            } else if ("OBR".equalsIgnoreCase(segment)) {
                if (fields.length > 4 && !fields[4].isEmpty()) {
                    title = fields[4].replace("^", " ");
                }
            } else if ("OBX".equalsIgnoreCase(segment)) {
                if (fields.length > 3 && !fields[3].isEmpty()) {
                    geneTarget = fields[3].replace("^", " ");
                }
                if (fields.length > 5 && !fields[5].isEmpty()) {
                    resultText = fields[5];
                }
            }
        }

        if (phn != null && !PhnValidator.isValidPhn(phn)) {
            response.put("status", "REJECTED");
            response.put("error", "Invalid BC PHN Modulus-11 checksum: " + PhnValidator.maskPhn(phn));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        PatientEntity patient;
        if (phn != null) {
            Optional<PatientEntity> found = patientRepository.findByPhn(phn);
            if (found.isPresent()) {
                patient = found.get();
            } else {
                patient = PatientEntity.builder()
                        .phn(phn)
                        .mrn("TF-HL7-" + System.currentTimeMillis() % 10000L)
                        .givenName(givenName)
                        .familyName(familyName)
                        .gender("unknown")
                        .build();
                patient = patientRepository.save(patient);
            }
        } else {
            patient = PatientEntity.builder()
                    .phn("9" + (System.currentTimeMillis() % 1000000000L))
                    .mrn("TF-HL7-" + System.currentTimeMillis() % 10000L)
                    .givenName(givenName)
                    .familyName(familyName)
                    .gender("unknown")
                    .build();
            patient = patientRepository.save(patient);
        }

        GenomicReportEntity report = GenomicReportEntity.builder()
                .patient(patient)
                .reportTitle(title)
                .specimenSource("Inbound HL7 v2 Tissue Specimen")
                .geneTarget(geneTarget)
                .mutationResult(resultText)
                .interpretation("Ingested via HL7 v2 ORU^R01 / MDM^T02 pipeline.")
                .pathologistName("HL7 Ingest Service")
                .testDate(LocalDate.now())
                .build();

        GenomicReportEntity savedReport = genomicRepository.save(report);

        log.info("[TERRY-FOX-HL7] HL7 Ingestion Successful. Created GenomicReport ID: {} for PHN: {}",
                savedReport.getId(), PhnValidator.maskPhn(patient.getPhn()));

        response.put("status", "SUCCESS");
        response.put("phn", PhnValidator.maskPhn(patient.getPhn()));
        response.put("genomicReportId", savedReport.getId());
        response.put("fhirReference", "DiagnosticReport/" + savedReport.getId());
        return ResponseEntity.ok(response);
    }
}
