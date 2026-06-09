package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "medications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // FHIR MedicationRequest aligned
    @Column(name = "medication_name", nullable = false)
    private String medicationName;

    @Column(name = "generic_name")
    private String genericName;

    @Column(name = "rxnorm_code")
    private String rxnormCode; // RxNorm code — standard in Seymour/FHIR

    @Column(name = "dose")
    private String dose; // e.g., "10 mg"

    @Column(name = "frequency")
    private String frequency; // e.g., "BID", "TID", "QD", "PRN"

    @Column(name = "route")
    private String route; // ORAL | IV | IM | TOPICAL | SUBLINGUAL | INHALED

    @Column(name = "status")
    private String status; // ACTIVE | COMPLETED | STOPPED | ON_HOLD | ENTERED_IN_ERROR

    @Column(name = "prescriber")
    private String prescriber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "indication")
    private String indication; // reason for medication

    @Column(name = "refills_remaining")
    private Integer refillsRemaining;

    @Column(name = "pharmacy")
    private String pharmacy;

    @Column(name = "special_instructions")
    private String specialInstructions;

    @Column(name = "visit_number")
    private String visitNumber; // ties to AdtEvent visit

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
