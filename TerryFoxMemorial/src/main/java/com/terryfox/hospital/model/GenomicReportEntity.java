package com.terryfox.hospital.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "terryfox_genomic_reports")
public class GenomicReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    @Column(nullable = false)
    private String reportTitle;

    private String specimenSource;
    private String geneTarget;
    private String mutationResult;
    private String interpretation;
    private String pathologistName;
    private LocalDate testDate;

    public GenomicReportEntity() {}

    public GenomicReportEntity(Long id, PatientEntity patient, String reportTitle, String specimenSource, String geneTarget, String mutationResult, String interpretation, String pathologistName, LocalDate testDate) {
        this.id = id;
        this.patient = patient;
        this.reportTitle = reportTitle;
        this.specimenSource = specimenSource;
        this.geneTarget = geneTarget;
        this.mutationResult = mutationResult;
        this.interpretation = interpretation;
        this.pathologistName = pathologistName;
        this.testDate = testDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PatientEntity getPatient() { return patient; }
    public void setPatient(PatientEntity patient) { this.patient = patient; }
    public String getReportTitle() { return reportTitle; }
    public void setReportTitle(String reportTitle) { this.reportTitle = reportTitle; }
    public String getSpecimenSource() { return specimenSource; }
    public void setSpecimenSource(String specimenSource) { this.specimenSource = specimenSource; }
    public String getGeneTarget() { return geneTarget; }
    public void setGeneTarget(String geneTarget) { this.geneTarget = geneTarget; }
    public String getMutationResult() { return mutationResult; }
    public void setMutationResult(String mutationResult) { this.mutationResult = mutationResult; }
    public String getInterpretation() { return interpretation; }
    public void setInterpretation(String interpretation) { this.interpretation = interpretation; }
    public String getPathologistName() { return pathologistName; }
    public void setPathologistName(String pathologistName) { this.pathologistName = pathologistName; }
    public LocalDate getTestDate() { return testDate; }
    public void setTestDate(LocalDate testDate) { this.testDate = testDate; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private PatientEntity patient;
        private String reportTitle;
        private String specimenSource;
        private String geneTarget;
        private String mutationResult;
        private String interpretation;
        private String pathologistName;
        private LocalDate testDate;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder patient(PatientEntity patient) { this.patient = patient; return this; }
        public Builder reportTitle(String reportTitle) { this.reportTitle = reportTitle; return this; }
        public Builder specimenSource(String specimenSource) { this.specimenSource = specimenSource; return this; }
        public Builder geneTarget(String geneTarget) { this.geneTarget = geneTarget; return this; }
        public Builder mutationResult(String mutationResult) { this.mutationResult = mutationResult; return this; }
        public Builder interpretation(String interpretation) { this.interpretation = interpretation; return this; }
        public Builder pathologistName(String pathologistName) { this.pathologistName = pathologistName; return this; }
        public Builder testDate(LocalDate testDate) { this.testDate = testDate; return this; }

        public GenomicReportEntity build() {
            return new GenomicReportEntity(id, patient, reportTitle, specimenSource, geneTarget, mutationResult, interpretation, pathologistName, testDate);
        }
    }
}
