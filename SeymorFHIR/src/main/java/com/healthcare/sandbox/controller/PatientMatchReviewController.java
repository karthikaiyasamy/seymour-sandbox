package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.model.PatientMatchReview;
import com.healthcare.sandbox.repository.PatientMatchReviewRepository;
import com.healthcare.sandbox.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fhir/Patient/match-reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PatientMatchReviewController {

    private final PatientMatchReviewRepository reviewRepo;
    private final PatientRepository patientRepo;

    /**
     * GET /api/fhir/Patient/match-reviews — List all pending conflict review records
     */
    @GetMapping
    public List<PatientMatchReview> getPendingReviews() {
        log.info("Fetching all PENDING_REVIEW patient conflict records");
        return reviewRepo.findByStatus("PENDING_REVIEW");
    }

    /**
     * POST /api/fhir/Patient/match-reviews/{id}/approve — Approve and create patient
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveConflictRecord(@PathVariable Long id) {
        log.info("Approving patient match conflict review record ID: {}", id);

        return reviewRepo.findById(id).map(review -> {
            review.setStatus("MANUALLY_APPROVED");
            review.setResolvedAt(LocalDateTime.now());
            review.setResolutionNotes("Manually verified and approved by HIM Officer.");
            reviewRepo.save(review);

            LocalDate dob = review.getInboundDob() != null && !review.getInboundDob().isEmpty()
                    ? LocalDate.parse(review.getInboundDob()) : null;

            Patient newPatient = patientRepo.save(Patient.builder()
                    .mrn(review.getInboundMrn())
                    .firstName(review.getInboundFirstName())
                    .lastName(review.getInboundLastName())
                    .dateOfBirth(dob)
                    .healthCardNumber(review.getInboundPhn())
                    .active(true)
                    .build());

            Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("status", "MANUALLY_APPROVED");
            resp.put("patientId", newPatient.getId());
            resp.put("mrn", newPatient.getMrn());

            return ResponseEntity.ok(resp);
        }).orElse(ResponseEntity.notFound().build());
    }
}
