package com.healthcare.sandbox.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "allergy_intolerances")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllergyIntolerance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "clinical_status")
    private String clinicalStatus; // active | inactive | resolved

    @Column(name = "verification_status")
    private String verificationStatus; // unconfirmed | confirmed | refuted

    @Column(name = "category")
    private String category; // food | medication | environment | biologic

    @Column(name = "criticality")
    private String criticality; // low | high | unable-to-assess

    @Column(name = "code")
    private String code; // SNOMED or RxNorm or local code (e.g., 297422002 for Penicillin allergy)

    @Column(name = "display", nullable = false)
    private String display; // e.g., "Allergy to Penicillin", "Peanut allergy"

    @Column(name = "reaction_manifestation")
    private String reactionManifestation; // e.g., "Hives", "Anaphylaxis", "Wheezing"

    @Column(name = "reaction_severity")
    private String reactionSeverity; // mild | moderate | severe

    @Column(name = "recorded_date")
    private LocalDateTime recordedDate;

    @PrePersist
    public void prePersist() {
        if (this.recordedDate == null) {
            this.recordedDate = LocalDateTime.now();
        }
        if (this.clinicalStatus == null) {
            this.clinicalStatus = "active";
        }
        if (this.verificationStatus == null) {
            this.verificationStatus = "confirmed";
        }
    }
}
