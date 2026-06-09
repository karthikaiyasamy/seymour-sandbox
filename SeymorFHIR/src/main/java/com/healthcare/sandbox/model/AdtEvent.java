package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "adt_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdtEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    // ADT Event Types (HL7 v2 aligned: A01=Admit, A02=Transfer, A03=Discharge, A04=Register, A08=Update)
    @Column(name = "event_type", nullable = false)
    private String eventType; // ADMIT | TRANSFER | DISCHARGE | REGISTER | UPDATE

    @Column(name = "event_code")
    private String eventCode; // A01, A02, A03, A04, A08

    @Column(name = "event_datetime", nullable = false)
    private LocalDateTime eventDatetime;

    @Column(name = "facility")
    private String facility; // e.g., "Vancouver General Hospital"

    @Column(name = "ward")
    private String ward; // e.g., "4 North", "ICU", "Emergency"

    @Column(name = "room")
    private String room;

    @Column(name = "bed")
    private String bed;

    @Column(name = "attending_physician")
    private String attendingPhysician;

    @Column(name = "admitting_diagnosis")
    private String admittingDiagnosis;

    @Column(name = "discharge_disposition")
    private String dischargeDisposition; // HOME | TRANSFER | EXPIRED | AMA (Against Medical Advice)

    @Column(name = "visit_number", nullable = false)
    private String visitNumber; // Encounter/Visit ID — like Seymour's FIN (Financial Encounter Number)

    @Column(name = "patient_class")
    private String patientClass; // INPATIENT | OUTPATIENT | EMERGENCY | OBSERVATION

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
