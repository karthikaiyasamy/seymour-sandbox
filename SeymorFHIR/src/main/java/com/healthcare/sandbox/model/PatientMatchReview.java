package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_match_reviews", indexes = {
        @Index(name = "idx_review_status", columnList = "status"),
        @Index(name = "idx_review_mrn", columnList = "inboundMrn")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientMatchReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String inboundMrn;

    private String inboundPhn;
    private String inboundFirstName;
    private String inboundLastName;
    private String inboundDob;

    private Double matchScore; // Score between 0.0 and 1.0 (e.g. 0.65 = PENDING_REVIEW)
    
    @Column(nullable = false)
    private String status; // PENDING_REVIEW, MANUALLY_APPROVED, REJECTED

    private String resolutionNotes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
