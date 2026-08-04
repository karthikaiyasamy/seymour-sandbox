package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.HeavyReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class ReportController {

    private final HeavyReportService heavyReportService;

    /**
     * Endpoint protected by Resilience4j Bulkhead Pattern.
     * GET /api/reports/heavy-export/{patientId}
     */
    @GetMapping("/heavy-export/{patientId}")
    public ResponseEntity<Map<String, Object>> exportHeavyReport(@PathVariable String patientId) {
        log.info("Incoming Heavy Export Request for Patient ID: {}", patientId);
        Map<String, Object> result = heavyReportService.generateHeavyPatientReport(patientId);

        if (result.containsKey("httpStatus") && (int) result.get("httpStatus") == 429) {
            return ResponseEntity.status(429).body(result);
        }

        return ResponseEntity.ok(result);
    }
}
