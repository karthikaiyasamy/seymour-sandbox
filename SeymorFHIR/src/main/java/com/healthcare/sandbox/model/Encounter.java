package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "encounters")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "visit_number", nullable = false)
    private String visitNumber;

    @Column(name = "encounter_type")
    private String encounterType; // SOAP_NOTE | DISCHARGE_SUMMARY | CONSULTATION | PROGRESS_NOTE | LAB_RESULT

    @Column(name = "encounter_datetime", nullable = false)
    private LocalDateTime encounterDatetime;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "provider_role")
    private String providerRole; // PHYSICIAN | NURSE | PHARMACIST | SPECIALIST

    @Column(name = "department")
    private String department;

    @Column(name = "chief_complaint", length = 500)
    private String chiefComplaint;

    // SOAP Note structure
    @Column(name = "subjective", length = 2000)
    private String subjective;

    @Column(name = "objective", length = 2000)
    private String objective;

    @Column(name = "assessment", length = 2000)
    private String assessment;

    @Column(name = "plan", length = 2000)
    private String plan;

    @Column(name = "diagnosis_code")
    private String diagnosisCode; // ICD-10 code

    @Column(name = "diagnosis_description")
    private String diagnosisDescription;

    @Column(name = "vitals_bp")
    private String vitalsBp; // e.g., "120/80"

    @Column(name = "vitals_hr")
    private Integer vitalsHr; // heart rate bpm

    @Column(name = "vitals_temp")
    private Double vitalsTemp; // Celsius

    @Column(name = "vitals_spo2")
    private Integer vitalsSpo2; // oxygen saturation %

    @Column(name = "vitals_weight_kg")
    private Double vitalsWeightKg;

    @Column(name = "status")
    private String status; // FINAL | DRAFT | AMENDED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
