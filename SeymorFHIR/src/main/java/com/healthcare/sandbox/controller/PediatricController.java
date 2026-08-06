package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.PatientRepository;
import com.healthcare.sandbox.service.Hl7Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/pediatric")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PediatricController {

    private final PatientRepository patientRepo;
    private final Hl7Service hl7Service;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String MIRTH_URL = "http://localhost:9085/";

    public record VaccineRequest(
            Long patientId,
            String vaccineCode,
            String vaccineName,
            String date,
            String lotNumber
    ) {}

    public record LabRequest(
            Long patientId,
            String testCode,
            String testName,
            String value,
            String unit,
            String flag,
            String date
    ) {}

    @PostMapping("/vaccine")
    public ResponseEntity<Map<String, String>> submitVaccine(@RequestBody VaccineRequest request) {
        log.info("Processing pediatric vaccine submission for Patient ID: {}, Code: {}", request.patientId(), request.vaccineCode());

        return patientRepo.findById(request.patientId()).map(patient -> {
            // Clean up date format if necessary
            String hl7Date = request.date().replaceAll("[-T:]", ""); // Format as yyyyMMddHHmmss
            if (hl7Date.length() > 14) {
                hl7Date = hl7Date.substring(0, 14);
            }

            String hl7Msg = hl7Service.generateVxu(patient, request.vaccineCode(), request.vaccineName(), hl7Date, request.lotNumber());
            
            sendHl7ToMirth(hl7Msg);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Vaccine HL7 successfully transmitted to Mirth!"
            ));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", "error",
                "message", "Patient with ID " + request.patientId() + " not found"
        )));
    }

    @PostMapping("/lab")
    public ResponseEntity<Map<String, String>> submitLab(@RequestBody LabRequest request) {
        log.info("Processing pediatric lab submission for Patient ID: {}, Test: {}", request.patientId(), request.testCode());

        return patientRepo.findById(request.patientId()).map(patient -> {
            // Clean up date format if necessary
            String hl7Date = request.date().replaceAll("[-T:]", ""); // Format as yyyyMMddHHmmss
            if (hl7Date.length() > 14) {
                hl7Date = hl7Date.substring(0, 14);
            }

            String hl7Msg = hl7Service.generateOru(patient, request.testCode(), request.testName(), request.value(), request.unit(), request.flag(), hl7Date);
            
            sendHl7ToMirth(hl7Msg);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Lab result HL7 successfully transmitted to Mirth!"
            ));
        }).orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "status", "error",
                "message", "Patient with ID " + request.patientId() + " not found"
        )));
    }

    private void sendHl7ToMirth(String rawHl7Message) {
        try {
            log.info("Transmitting HL7 v2 message to Mirth Connect interface engine at [{}]", MIRTH_URL);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);

            HttpEntity<String> request = new HttpEntity<>(rawHl7Message, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(MIRTH_URL, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("HL7 successfully transmitted to Mirth!");
            } else {
                log.warn("Mirth returned status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to transmit HL7 message to Mirth: {}", e.getMessage(), e);
        }
    }
}
