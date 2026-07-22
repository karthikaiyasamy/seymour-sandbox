package com.langley.hospital.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "langley_allergies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LangleyAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private LangleyPatient patient;

    @Column(name = "allergy_code")
    private String allergyCode;

    @Column(name = "allergy_display", nullable = false)
    private String allergyDisplay;

    @Column(name = "category")
    private String category; // food | medication | environment

    @Column(name = "criticality")
    private String criticality; // low | high

    @Column(name = "reaction")
    private String reaction;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @PrePersist
    public void prePersist() {
        if (this.syncedAt == null) {
            this.syncedAt = LocalDateTime.now();
        }
    }
}
