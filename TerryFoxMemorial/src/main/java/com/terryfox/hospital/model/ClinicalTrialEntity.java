package com.terryfox.hospital.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "terryfox_clinical_trials")
public class ClinicalTrialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nctId;

    @Column(nullable = false)
    private String title;

    private String phase;
    private String sponsor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private PatientEntity patient;

    private String subjectStatus;
    private String assignedArm;
    private LocalDate enrollmentDate;

    public ClinicalTrialEntity() {}

    public ClinicalTrialEntity(Long id, String nctId, String title, String phase, String sponsor, PatientEntity patient, String subjectStatus, String assignedArm, LocalDate enrollmentDate) {
        this.id = id;
        this.nctId = nctId;
        this.title = title;
        this.phase = phase;
        this.sponsor = sponsor;
        this.patient = patient;
        this.subjectStatus = subjectStatus;
        this.assignedArm = assignedArm;
        this.enrollmentDate = enrollmentDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNctId() { return nctId; }
    public void setNctId(String nctId) { this.nctId = nctId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getSponsor() { return sponsor; }
    public void setSponsor(String sponsor) { this.sponsor = sponsor; }
    public PatientEntity getPatient() { return patient; }
    public void setPatient(PatientEntity patient) { this.patient = patient; }
    public String getSubjectStatus() { return subjectStatus; }
    public void setSubjectStatus(String subjectStatus) { this.subjectStatus = subjectStatus; }
    public String getAssignedArm() { return assignedArm; }
    public void setAssignedArm(String assignedArm) { this.assignedArm = assignedArm; }
    public LocalDate getEnrollmentDate() { return enrollmentDate; }
    public void setEnrollmentDate(LocalDate enrollmentDate) { this.enrollmentDate = enrollmentDate; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String nctId;
        private String title;
        private String phase;
        private String sponsor;
        private PatientEntity patient;
        private String subjectStatus;
        private String assignedArm;
        private LocalDate enrollmentDate;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder nctId(String nctId) { this.nctId = nctId; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder phase(String phase) { this.phase = phase; return this; }
        public Builder sponsor(String sponsor) { this.sponsor = sponsor; return this; }
        public Builder patient(PatientEntity patient) { this.patient = patient; return this; }
        public Builder subjectStatus(String subjectStatus) { this.subjectStatus = subjectStatus; return this; }
        public Builder assignedArm(String assignedArm) { this.assignedArm = assignedArm; return this; }
        public Builder enrollmentDate(LocalDate enrollmentDate) { this.enrollmentDate = enrollmentDate; return this; }

        public ClinicalTrialEntity build() {
            return new ClinicalTrialEntity(id, nctId, title, phase, sponsor, patient, subjectStatus, assignedArm, enrollmentDate);
        }
    }
}
