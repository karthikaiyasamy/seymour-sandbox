package com.healthcare.sandbox.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class HeavyReportService {

    /**
     * Heavy export operation protected by Resilience4j Bulkhead Pattern.
     * Max 2 concurrent executions permitted. Overflow calls trigger fallback.
     */
    @Bulkhead(name = "reportBulkhead", fallbackMethod = "fallbackLabReport")
    public Map<String, Object> generateHeavyPatientReport(String patientId) {
        log.info("⚓ [BULKHEAD ACTIVE] Processing heavy lab report for Patient ID: {} on thread: {}", 
                patientId, Thread.currentThread().getName());

        // Simulate 4 seconds of heavy calculation / PDF generation
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceType", "DocumentReference");
        result.put("status", "current");
        result.put("description", "Comprehensive Longitudinal Patient EHR Summary");
        result.put("patientId", patientId);
        result.put("executionThread", Thread.currentThread().getName());
        result.put("message", "Successfully generated heavy report within Bulkhead capacity.");
        return result;
    }

    /**
     * Fallback method executed when Bulkhead capacity is full (BulkheadFullException).
     */
    public Map<String, Object> fallbackLabReport(String patientId, Throwable t) {
        log.warn("⚠️ [BULKHEAD REJECTED] Bulkhead pool capacity reached for Patient ID: {}. Triggering graceful fallback. Cause: {}", 
                patientId, t.getMessage());

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("resourceType", "OperationOutcome");
        fallback.put("status", "DEGRADED_CAPACITY");
        fallback.put("patientId", patientId);
        fallback.put("httpStatus", 429);
        fallback.put("message", "Heavy export capacity reached. Request queued for background processing to protect critical API services.");
        fallback.put("bulkheadReason", t.getClass().getSimpleName() + ": " + t.getMessage());
        return fallback;
    }
}
