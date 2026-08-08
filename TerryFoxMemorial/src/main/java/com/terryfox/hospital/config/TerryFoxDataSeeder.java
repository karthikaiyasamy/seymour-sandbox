package com.terryfox.hospital.config;

import com.terryfox.hospital.model.*;
import com.terryfox.hospital.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class TerryFoxDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TerryFoxDataSeeder.class);

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private OncologyConditionRepository conditionRepository;

    @Autowired
    private ClinicalTrialRepository trialRepository;

    @Autowired
    private GenomicReportRepository genomicRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("[TERRY-FOX-SEEDER] Initializing synthetic BC Oncology & Clinical Trial datasets...");

        // 0. Patient: Margaret Chen (Stage IIIA Non-Small Cell Lung Cancer - EMPI Identity Link to Seymour)
        PatientEntity margaret = PatientEntity.builder()
                .phn("MRN-10001") // Matching Seymour PHN
                .mrn("TF-ONC-2026-000")
                .givenName("Margaret A.")
                .familyName("Chen")
                .birthDate(LocalDate.of(1948, 3, 15)) // ⚠️ 3-Day DOB Discrepancy from Seymour's 1948-03-12
                .gender("female")
                .addressLine("145 Maple Street")
                .city("Vancouver")
                .state("BC")
                .postalCode("V6J 2P3")
                .phone("604-555-0188")
                .primaryOncologist("Dr. Evelyn Vance, MD (Oncology)")
                .build();
        patientRepository.save(margaret);

        // Margaret's Oncology Staging Condition (mCODE)
        OncologyConditionEntity margaretCancer = OncologyConditionEntity.builder()
                .patient(margaret)
                .diagnosisCode("C34.1")
                .diagnosisDisplay("Malignant neoplasm of upper lobe, bronchus or lung")
                .codeSystem("http://hl7.org/fhir/sid/icd-10")
                .clinicalStatus("active")
                .verificationStatus("confirmed")
                .tnmStageGroup("Stage IIIA")
                .primaryTumorCategory("T2")
                .regionalNodesCategory("N2")
                .distantMetastasisCategory("M0")
                .anatomicalSite("Right Upper Lobe Lung")
                .onsetDate(LocalDate.of(2025, 10, 5))
                .recordedDate(LocalDate.of(2025, 10, 10))
                .build();
        conditionRepository.save(margaretCancer);

        // 1. Patient: Sarah Jenkins (Stage IIIb Non-Small Cell Lung Cancer)
        PatientEntity sarah = PatientEntity.builder()
                .phn("9234567897") // Valid BC PHN Modulus-11
                .mrn("TF-ONC-2026-001")
                .givenName("Sarah")
                .familyName("Jenkins")
                .birthDate(LocalDate.of(1972, 4, 15))
                .gender("female")
                .addressLine("4500 Oak Street")
                .city("Vancouver")
                .state("BC")
                .postalCode("V6H 3N1")
                .phone("604-555-0199")
                .primaryOncologist("Dr. Evelyn Vance, MD (Oncology)")
                .build();
        patientRepository.save(sarah);

        // Sarah's Cancer Staging Condition (mCODE)
        OncologyConditionEntity sarahCancer = OncologyConditionEntity.builder()
                .patient(sarah)
                .diagnosisCode("C34.1")
                .diagnosisDisplay("Malignant neoplasm of upper lobe, bronchus or lung")
                .codeSystem("http://hl7.org/fhir/sid/icd-10")
                .clinicalStatus("active")
                .verificationStatus("confirmed")
                .tnmStageGroup("Stage IIIb")
                .primaryTumorCategory("T3")
                .regionalNodesCategory("N2")
                .distantMetastasisCategory("M0")
                .anatomicalSite("Left Upper Lobe Lung")
                .onsetDate(LocalDate.of(2025, 11, 10))
                .recordedDate(LocalDate.of(2025, 11, 12))
                .build();
        conditionRepository.save(sarahCancer);

        // Sarah's Clinical Trial Enrollment
        ClinicalTrialEntity sarahTrial = ClinicalTrialEntity.builder()
                .patient(sarah)
                .nctId("NCT05123456")
                .title("BC Cancer Phase II Targeted Immunotherapy Trial for EGFR+ NSCLC")
                .phase("Phase II")
                .sponsor("BC Cancer Agency & Terry Fox Research Institute")
                .subjectStatus("enrolled")
                .assignedArm("Arm A: Osimertinib + Pembrolizumab Infusion")
                .enrollmentDate(LocalDate.of(2026, 1, 15))
                .build();
        trialRepository.save(sarahTrial);

        // Sarah's NGS Genomic Biomarker Report
        GenomicReportEntity sarahGenomics = GenomicReportEntity.builder()
                .patient(sarah)
                .reportTitle("Solid Tumor Comprehensive NGS Biomarker Panel")
                .specimenSource("Core Needle Biopsy - Left Lung Mass")
                .geneTarget("EGFR")
                .mutationResult("Exon 19 Deletion (p.Glu746_Ala750del) POSITIVE")
                .interpretation("Pathogenic. High sensitivity predicted to 3rd generation EGFR Tyrosine Kinase Inhibitors.")
                .pathologistName("Dr. Marcus Thorne, FRCPC (Molecular Pathology)")
                .testDate(LocalDate.of(2025, 11, 20))
                .build();
        genomicRepository.save(sarahGenomics);

        // 2. Patient: Robert Chen (Stage IV Colorectal Carcinoma)
        PatientEntity robert = PatientEntity.builder()
                .phn("9234567826") // Valid BC PHN Modulus-11
                .mrn("TF-ONC-2026-002")
                .givenName("Robert")
                .familyName("Chen")
                .birthDate(LocalDate.of(1965, 9, 28))
                .gender("male")
                .addressLine("8888 University Drive")
                .city("Burnaby")
                .state("BC")
                .postalCode("V5A 1S6")
                .phone("604-555-0888")
                .primaryOncologist("Dr. Aris Thorne, MD (Gastrointestinal Oncology)")
                .build();
        patientRepository.save(robert);

        // Robert's Cancer Staging Condition (mCODE)
        OncologyConditionEntity robertCancer = OncologyConditionEntity.builder()
                .patient(robert)
                .diagnosisCode("C18.7")
                .diagnosisDisplay("Malignant neoplasm of sigmoid colon")
                .codeSystem("http://hl7.org/fhir/sid/icd-10")
                .clinicalStatus("active")
                .verificationStatus("confirmed")
                .tnmStageGroup("Stage IV")
                .primaryTumorCategory("T4a")
                .regionalNodesCategory("N2b")
                .distantMetastasisCategory("M1a (Hepatic)")
                .anatomicalSite("Sigmoid Colon / Liver Metastasis")
                .onsetDate(LocalDate.of(2025, 8, 5))
                .recordedDate(LocalDate.of(2025, 8, 8))
                .build();
        conditionRepository.save(robertCancer);

        // Robert's Clinical Trial
        ClinicalTrialEntity robertTrial = ClinicalTrialEntity.builder()
                .patient(robert)
                .nctId("NCT04987654")
                .title("Multi-Center Adoptive T-Cell Therapy for KRAS Wild-Type Metastatic Colorectal Cancer")
                .phase("Phase III")
                .sponsor("Terry Fox Memorial Cancer Center")
                .subjectStatus("active")
                .assignedArm("Arm B: mFOLFOX6 + Panitumumab + TIL Infusion")
                .enrollmentDate(LocalDate.of(2025, 9, 1))
                .build();
        trialRepository.save(robertTrial);

        // Robert's Biomarker Report
        GenomicReportEntity robertGenomics = GenomicReportEntity.builder()
                .patient(robert)
                .reportTitle("Liquid Biopsy ctDNA Panel & PD-L1 IHC")
                .specimenSource("Peripheral Blood plasma ctDNA")
                .geneTarget("KRAS / PD-L1")
                .mutationResult("KRAS Codon 12/13/61 Wild Type | PD-L1 Tumor Proportion Score (TPS): 85%")
                .interpretation("Wild Type KRAS supports Anti-EGFR monoclonal antibody responsiveness.")
                .pathologistName("Dr. Sarah Al-Mansoor, FRCPC")
                .testDate(LocalDate.of(2025, 8, 15))
                .build();
        genomicRepository.save(robertGenomics);

        log.info("[TERRY-FOX-SEEDER] Seeding complete! 2 Oncology cohorts loaded with mCODE staging, trials, and genomics.");
    }
}
