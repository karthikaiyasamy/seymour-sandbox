package com.langley.hospital.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "langley_lab_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LangleyLabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private LangleyPatient patient;

    @Column(name = "test_code", nullable = false)
    private String testCode;

    @Column(name = "test_name")
    private String testName;

    @Column(name = "test_date")
    private LocalDateTime testDate;

    @Column(name = "result_value")
    private String resultValue;

    @Column(name = "unit")
    private String unit;

    @Column(name = "flag")
    private String flag; // N (normal), A (abnormal), etc.

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
