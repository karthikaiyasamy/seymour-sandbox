package com.terryfox.hospital.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "terryfox_oncology_conditions")
public class OncologyConditionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    @Column(nullable = false)
    private String diagnosisCode;

    @Column(nullable = false)
    private String diagnosisDisplay;

    private String codeSystem;
    private String clinicalStatus;
    private String verificationStatus;

    private String tnmStageGroup;
    private String primaryTumorCategory;
    private String regionalNodesCategory;
    private String distantMetastasisCategory;

    private String anatomicalSite;
    private LocalDate onsetDate;
    private LocalDate recordedDate;

    public OncologyConditionEntity() {}

    public OncologyConditionEntity(Long id, PatientEntity patient, String diagnosisCode, String diagnosisDisplay, String codeSystem, String clinicalStatus, String verificationStatus, String tnmStageGroup, String primaryTumorCategory, String regionalNodesCategory, String distantMetastasisCategory, String anatomicalSite, LocalDate onsetDate, LocalDate recordedDate) {
        this.id = id;
        this.patient = patient;
        this.diagnosisCode = diagnosisCode;
        this.diagnosisDisplay = diagnosisDisplay;
        this.codeSystem = codeSystem;
        this.clinicalStatus = clinicalStatus;
        this.verificationStatus = verificationStatus;
        this.tnmStageGroup = tnmStageGroup;
        this.primaryTumorCategory = primaryTumorCategory;
        this.regionalNodesCategory = regionalNodesCategory;
        this.distantMetastasisCategory = distantMetastasisCategory;
        this.anatomicalSite = anatomicalSite;
        this.onsetDate = onsetDate;
        this.recordedDate = recordedDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PatientEntity getPatient() { return patient; }
    public void setPatient(PatientEntity patient) { this.patient = patient; }
    public String getDiagnosisCode() { return diagnosisCode; }
    public void setDiagnosisCode(String diagnosisCode) { this.diagnosisCode = diagnosisCode; }
    public String getDiagnosisDisplay() { return diagnosisDisplay; }
    public void setDiagnosisDisplay(String diagnosisDisplay) { this.diagnosisDisplay = diagnosisDisplay; }
    public String getCodeSystem() { return codeSystem; }
    public void setCodeSystem(String codeSystem) { this.codeSystem = codeSystem; }
    public String getClinicalStatus() { return clinicalStatus; }
    public void setClinicalStatus(String clinicalStatus) { this.clinicalStatus = clinicalStatus; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getTnmStageGroup() { return tnmStageGroup; }
    public void setTnmStageGroup(String tnmStageGroup) { this.tnmStageGroup = tnmStageGroup; }
    public String getPrimaryTumorCategory() { return primaryTumorCategory; }
    public void setPrimaryTumorCategory(String primaryTumorCategory) { this.primaryTumorCategory = primaryTumorCategory; }
    public String getRegionalNodesCategory() { return regionalNodesCategory; }
    public void setRegionalNodesCategory(String regionalNodesCategory) { this.regionalNodesCategory = regionalNodesCategory; }
    public String getDistantMetastasisCategory() { return distantMetastasisCategory; }
    public void setDistantMetastasisCategory(String distantMetastasisCategory) { this.distantMetastasisCategory = distantMetastasisCategory; }
    public String getAnatomicalSite() { return anatomicalSite; }
    public void setAnatomicalSite(String anatomicalSite) { this.anatomicalSite = anatomicalSite; }
    public LocalDate getOnsetDate() { return onsetDate; }
    public void setOnsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; }
    public LocalDate getRecordedDate() { return recordedDate; }
    public void setRecordedDate(LocalDate recordedDate) { this.recordedDate = recordedDate; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private PatientEntity patient;
        private String diagnosisCode;
        private String diagnosisDisplay;
        private String codeSystem;
        private String clinicalStatus;
        private String verificationStatus;
        private String tnmStageGroup;
        private String primaryTumorCategory;
        private String regionalNodesCategory;
        private String distantMetastasisCategory;
        private String anatomicalSite;
        private LocalDate onsetDate;
        private LocalDate recordedDate;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder patient(PatientEntity patient) { this.patient = patient; return this; }
        public Builder diagnosisCode(String diagnosisCode) { this.diagnosisCode = diagnosisCode; return this; }
        public Builder diagnosisDisplay(String diagnosisDisplay) { this.diagnosisDisplay = diagnosisDisplay; return this; }
        public Builder codeSystem(String codeSystem) { this.codeSystem = codeSystem; return this; }
        public Builder clinicalStatus(String clinicalStatus) { this.clinicalStatus = clinicalStatus; return this; }
        public Builder verificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; return this; }
        public Builder tnmStageGroup(String tnmStageGroup) { this.tnmStageGroup = tnmStageGroup; return this; }
        public Builder primaryTumorCategory(String primaryTumorCategory) { this.primaryTumorCategory = primaryTumorCategory; return this; }
        public Builder regionalNodesCategory(String regionalNodesCategory) { this.regionalNodesCategory = regionalNodesCategory; return this; }
        public Builder distantMetastasisCategory(String distantMetastasisCategory) { this.distantMetastasisCategory = distantMetastasisCategory; return this; }
        public Builder anatomicalSite(String anatomicalSite) { this.anatomicalSite = anatomicalSite; return this; }
        public Builder onsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; return this; }
        public Builder recordedDate(LocalDate recordedDate) { this.recordedDate = recordedDate; return this; }

        public OncologyConditionEntity build() {
            return new OncologyConditionEntity(id, patient, diagnosisCode, diagnosisDisplay, codeSystem, clinicalStatus, verificationStatus, tnmStageGroup, primaryTumorCategory, regionalNodesCategory, distantMetastasisCategory, anatomicalSite, onsetDate, recordedDate);
        }
    }
}
