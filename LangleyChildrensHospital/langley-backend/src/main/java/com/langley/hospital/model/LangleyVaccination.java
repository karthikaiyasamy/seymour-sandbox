package com.langley.hospital.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "langley_vaccinations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LangleyVaccination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private LangleyPatient patient;

    @Column(name = "vaccine_code", nullable = false)
    private String vaccineCode;

    @Column(name = "vaccine_name")
    private String vaccineName;

    @Column(name = "administration_date")
    private LocalDateTime administrationDate;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "administered_by")
    private String administeredBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
