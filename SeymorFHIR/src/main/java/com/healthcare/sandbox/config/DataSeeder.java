package com.healthcare.sandbox.config;

import com.healthcare.sandbox.model.*;
import com.healthcare.sandbox.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final PatientRepository patientRepo;
    private final AdtEventRepository adtRepo;
    private final MedicationRepository medRepo;
    private final EncounterRepository encRepo;
    private final AllergyIntoleranceRepository allergyRepo;
    private final ObservationRepository observationRepo;

    @Override
    public void run(String... args) {
        if (patientRepo.count() > 0) {
            log.info("Database already seeded. Skipping.");
            return;
        }

        log.info("Seeding synthetic patient data...");

        // ── Patient 1: Diabetic elderly patient with recent admission ──
        Patient p1 = patientRepo.save(Patient.builder()
                .mrn("MRN-10001")
                .firstName("Margaret")
                .lastName("Chen")
                .dateOfBirth(LocalDate.of(1948, 3, 12))
                .gender("female")
                .phone("604-555-0101")
                .email("m.chen.patient@example.com")
                .addressLine("145 Maple Street")
                .city("Vancouver")
                .province("BC")
                .postalCode("V5K 1A1")
                .healthCardNumber("BC9001234567")
                .bloodType("A+")
                .allergies("Penicillin, Sulfa drugs")
                .primaryLanguage("English")
                .active(true)
                .build());

        // ADT: Admit → Transfer → Discharge
        adtRepo.save(AdtEvent.builder()
                .patient(p1).eventType("ADMIT").eventCode("A01")
                .eventDatetime(LocalDateTime.now().minusDays(10))
                .facility("Vancouver General Hospital").ward("4 North").room("412").bed("A")
                .attendingPhysician("Dr. Sarah Park").admittingDiagnosis("Type 2 Diabetes - Uncontrolled, Hyperglycemia")
                .visitNumber("VN-2024-88001").patientClass("INPATIENT")
                .notes("Patient presented with blood glucose 28 mmol/L, lethargy, and confusion.").build());

        adtRepo.save(AdtEvent.builder()
                .patient(p1).eventType("TRANSFER").eventCode("A02")
                .eventDatetime(LocalDateTime.now().minusDays(7))
                .facility("Vancouver General Hospital").ward("Telemetry").room("516").bed("B")
                .attendingPhysician("Dr. Sarah Park")
                .visitNumber("VN-2024-88001").patientClass("INPATIENT")
                .notes("Transferred to Telemetry for cardiac monitoring after EKG changes noted.").build());

        adtRepo.save(AdtEvent.builder()
                .patient(p1).eventType("DISCHARGE").eventCode("A03")
                .eventDatetime(LocalDateTime.now().minusDays(3))
                .facility("Vancouver General Hospital")
                .attendingPhysician("Dr. Sarah Park")
                .dischargeDisposition("HOME")
                .visitNumber("VN-2024-88001").patientClass("INPATIENT")
                .notes("Discharged with endocrinology follow-up in 2 weeks. Insulin regimen adjusted.").build());

        // Observations (Vitals & Lab Results) for p1
        observationRepo.save(Observation.builder()
                .patient(p1).category("vital-signs").code("8867-4").codeSystem("http://loinc.org").codeDisplay("Heart Rate")
                .valueQuantity(88.0).valueUnit("beats/min").interpretation("N").effectiveDateTime(LocalDateTime.now().minusDays(8))
                .status("final").issued(LocalDateTime.now().minusDays(8)).build());

        observationRepo.save(Observation.builder()
                .patient(p1).category("vital-signs").code("8480-6").codeSystem("http://loinc.org").codeDisplay("Systolic Blood Pressure")
                .valueQuantity(148.0).valueUnit("mmHg").interpretation("H").effectiveDateTime(LocalDateTime.now().minusDays(8))
                .status("final").issued(LocalDateTime.now().minusDays(8)).build());

        observationRepo.save(Observation.builder()
                .patient(p1).category("laboratory").code("15074-8").codeSystem("http://loinc.org").codeDisplay("Glucose [Moles/volume] in Blood")
                .valueQuantity(28.4).valueUnit("mmol/L").interpretation("HH").effectiveDateTime(LocalDateTime.now().minusDays(8))
                .status("final").issued(LocalDateTime.now().minusDays(8)).build());

        observationRepo.save(Observation.builder()
                .patient(p1).category("laboratory").code("4548-4").codeSystem("http://loinc.org").codeDisplay("Hemoglobin A1c/Hemoglobin.total in Blood")
                .valueQuantity(11.2).valueUnit("%").interpretation("H").effectiveDateTime(LocalDateTime.now().minusDays(8))
                .status("final").issued(LocalDateTime.now().minusDays(8)).build());
        medRepo.save(Medication.builder()
                .patient(p1).medicationName("Metformin").genericName("metformin hydrochloride")
                .rxnormCode("860975").dose("1000 mg").frequency("BID").route("ORAL")
                .status("ACTIVE").prescriber("Dr. Sarah Park")
                .startDate(LocalDate.now().minusMonths(6)).indication("Type 2 Diabetes Mellitus")
                .refillsRemaining(5).pharmacy("London Drugs - Main St")
                .visitNumber("VN-2024-88001").build());

        medRepo.save(Medication.builder()
                .patient(p1).medicationName("Insulin Glargine").genericName("insulin glargine")
                .rxnormCode("274783").dose("20 units").frequency("QHS").route("SUBCUTANEOUS")
                .status("ACTIVE").prescriber("Dr. Sarah Park")
                .startDate(LocalDate.now().minusDays(3)).indication("Uncontrolled Type 2 Diabetes")
                .specialInstructions("Inject at bedtime. Rotate injection sites.")
                .refillsRemaining(3).pharmacy("London Drugs - Main St")
                .visitNumber("VN-2024-88001").build());

        medRepo.save(Medication.builder()
                .patient(p1).medicationName("Lisinopril").genericName("lisinopril")
                .rxnormCode("104375").dose("10 mg").frequency("QD").route("ORAL")
                .status("ACTIVE").prescriber("Dr. Sarah Park")
                .startDate(LocalDate.now().minusYears(2)).indication("Hypertension, Diabetic nephropathy protection")
                .refillsRemaining(11).pharmacy("London Drugs - Main St").build());

        // Encounter (SOAP note) for p1
        encRepo.save(Encounter.builder()
                .patient(p1).visitNumber("VN-2024-88001")
                .encounterType("PROGRESS_NOTE")
                .encounterDatetime(LocalDateTime.now().minusDays(8))
                .providerName("Dr. Sarah Park").providerRole("PHYSICIAN")
                .department("Internal Medicine")
                .chiefComplaint("Poorly controlled blood sugars, fatigue, polydipsia")
                .subjective("76-year-old female with 15-year history of T2DM. Reports fatigue, excessive thirst, " +
                        "and frequent urination x 5 days. States she missed last two endocrinology appointments.")
                .objective("BP 148/92 mmHg, HR 88 bpm, Temp 37.1°C, SpO2 96%. " +
                        "Blood glucose on admission 28.4 mmol/L. HbA1c 11.2%. Mild pedal edema bilateral.")
                .assessment("1. Type 2 Diabetes Mellitus - severely uncontrolled. " +
                        "2. Hypertension - suboptimal control. " +
                        "3. Possible early diabetic nephropathy - creatinine trending up.")
                .plan("1. IV fluid hydration. Sliding scale insulin. Endocrinology consult. " +
                        "2. Uptitrate Lisinopril. Nephrology referral. " +
                        "3. Diabetes education re: medication adherence and diet.")
                .diagnosisCode("E11.65").diagnosisDescription("Type 2 diabetes mellitus with hyperglycemia")
                .vitalsBp("148/92").vitalsHr(88).vitalsTemp(37.1).vitalsSpo2(96).vitalsWeightKg(74.2)
                .status("FINAL").build());

        encRepo.save(Encounter.builder()
                .patient(p1).visitNumber("VN-2024-88001")
                .encounterType("DISCHARGE_SUMMARY")
                .encounterDatetime(LocalDateTime.now().minusDays(3))
                .providerName("Dr. Sarah Park").providerRole("PHYSICIAN")
                .department("Internal Medicine")
                .chiefComplaint("Discharge Summary")
                .subjective("Patient improved significantly over 7-day admission.")
                .objective("Blood glucose stabilized 6–9 mmol/L on day of discharge. BP 128/78 on adjusted meds.")
                .assessment("T2DM now controlled. Cardiac workup negative for acute ischemia.")
                .plan("Follow-up endocrinology 2 weeks. Home glucose monitoring QID. Return to ED if glucose >15 mmol/L.")
                .diagnosisCode("E11.65").diagnosisDescription("Type 2 diabetes mellitus with hyperglycemia")
                .vitalsBp("128/78").vitalsHr(76).vitalsTemp(36.8).vitalsSpo2(98).vitalsWeightKg(73.5)
                .status("FINAL").build());

        // ── Patient 2: Post-op cardiac patient ──
        Patient p2 = patientRepo.save(Patient.builder()
                .mrn("MRN-10002")
                .firstName("Robert")
                .lastName("Okafor")
                .dateOfBirth(LocalDate.of(1962, 11, 28))
                .gender("male")
                .phone("778-555-0234")
                .email("r.okafor.patient@example.com")
                .addressLine("880 Robson Street")
                .city("Vancouver")
                .province("BC")
                .postalCode("V6Z 2B5")
                .healthCardNumber("BC9007654321")
                .bloodType("O-")
                .allergies("Aspirin (GI intolerance), Codeine")
                .primaryLanguage("English")
                .active(true)
                .build());

        adtRepo.save(AdtEvent.builder()
                .patient(p2).eventType("ADMIT").eventCode("A01")
                .eventDatetime(LocalDateTime.now().minusDays(5))
                .facility("St. Paul's Hospital").ward("Cardiac Surgery ICU").room("ICU-3").bed("A")
                .attendingPhysician("Dr. James Whitfield").admittingDiagnosis("STEMI - Anterior Wall")
                .visitNumber("VN-2024-77002").patientClass("INPATIENT")
                .notes("62M brought by EMS with chest pain x 2hrs, diaphoresis. EKG: ST elevation V1-V4. " +
                        "Taken directly to cath lab. Drug-eluting stent placed in LAD.").build());

        adtRepo.save(AdtEvent.builder()
                .patient(p2).eventType("TRANSFER").eventCode("A02")
                .eventDatetime(LocalDateTime.now().minusDays(3))
                .facility("St. Paul's Hospital").ward("Cardiology Step-Down").room("308").bed("B")
                .attendingPhysician("Dr. James Whitfield")
                .visitNumber("VN-2024-77002").patientClass("INPATIENT")
                .notes("Stable post-PCI. Transferred out of CSICU to step-down unit.").build());

        medRepo.save(Medication.builder()
                .patient(p2).medicationName("Ticagrelor").genericName("ticagrelor")
                .rxnormCode("1116632").dose("90 mg").frequency("BID").route("ORAL")
                .status("ACTIVE").prescriber("Dr. James Whitfield")
                .startDate(LocalDate.now().minusDays(5)).indication("Post-PCI dual antiplatelet therapy")
                .specialInstructions("Do not stop without consulting cardiologist. Avoid aspirin.")
                .refillsRemaining(0).pharmacy("St. Paul's Pharmacy")
                .visitNumber("VN-2024-77002").build());

        medRepo.save(Medication.builder()
                .patient(p2).medicationName("Rosuvastatin").genericName("rosuvastatin calcium")
                .rxnormCode("301542").dose("40 mg").frequency("QD").route("ORAL")
                .status("ACTIVE").prescriber("Dr. James Whitfield")
                .startDate(LocalDate.now().minusDays(5)).indication("Post-MI high-intensity statin therapy")
                .refillsRemaining(2).pharmacy("St. Paul's Pharmacy")
                .visitNumber("VN-2024-77002").build());

        medRepo.save(Medication.builder()
                .patient(p2).medicationName("Metoprolol Succinate").genericName("metoprolol succinate")
                .rxnormCode("866514").dose("50 mg").frequency("QD").route("ORAL")
                .status("ACTIVE").prescriber("Dr. James Whitfield")
                .startDate(LocalDate.now().minusDays(5)).indication("Post-MI cardioprotection, heart rate control")
                .refillsRemaining(2).pharmacy("St. Paul's Pharmacy")
                .visitNumber("VN-2024-77002").build());

        encRepo.save(Encounter.builder()
                .patient(p2).visitNumber("VN-2024-77002")
                .encounterType("SOAP_NOTE")
                .encounterDatetime(LocalDateTime.now().minusDays(4))
                .providerName("Dr. James Whitfield").providerRole("PHYSICIAN")
                .department("Cardiology")
                .chiefComplaint("Post-PCI day 1 assessment")
                .subjective("Patient reports mild chest soreness at cath site. No angina. " +
                        "Tolerating oral medications well. Concerned about returning to work.")
                .objective("BP 118/72, HR 62 bpm (on metoprolol), SpO2 99%. " +
                        "Troponin trending down. Echo: EF 45%. Cath site clean, no hematoma.")
                .assessment("Successful primary PCI for anterior STEMI. Hemodynamically stable. " +
                        "Reduced EF — will need repeat echo in 6 weeks.")
                .plan("Continue dual antiplatelet, statin, beta blocker. Cardiac rehab referral. " +
                        "Repeat echo 6 weeks. No driving for 1 week. Light activity only.")
                .diagnosisCode("I21.09").diagnosisDescription("ST elevation myocardial infarction involving other coronary artery")
                .vitalsBp("118/72").vitalsHr(62).vitalsTemp(36.9).vitalsSpo2(99).vitalsWeightKg(88.1)
                .status("FINAL").build());

        // ── Patient 3: Pediatric patient - asthma exacerbation ──
        Patient p3 = patientRepo.save(Patient.builder()
                .mrn("MRN-10003")
                .firstName("Aisha")
                .lastName("Patel")
                .dateOfBirth(LocalDate.of(2012, 7, 5))
                .gender("female")
                .phone("604-555-0388")
                .email("patel.family@example.com")
                .addressLine("3300 Kingsway")
                .city("Burnaby")
                .province("BC")
                .postalCode("V5R 5K6")
                .healthCardNumber("BC9003456789")
                .bloodType("B+")
                .allergies("Peanuts (anaphylaxis), Amoxicillin (rash)")
                .primaryLanguage("English")
                .active(true)
                .build());

        adtRepo.save(AdtEvent.builder()
                .patient(p3).eventType("REGISTER").eventCode("A04")
                .eventDatetime(LocalDateTime.now().minusDays(2))
                .facility("BC Children's Hospital").ward("Emergency").room("ED-12").bed("A")
                .attendingPhysician("Dr. Priya Mehta").admittingDiagnosis("Acute Asthma Exacerbation - Moderate")
                .visitNumber("VN-2024-66003").patientClass("EMERGENCY")
                .notes("11F with known asthma. Brought by parents: increased WOB, SpO2 88% on arrival, wheeze audible.").build());

        adtRepo.save(AdtEvent.builder()
                .patient(p3).eventType("ADMIT").eventCode("A01")
                .eventDatetime(LocalDateTime.now().minusDays(2).plusHours(3))
                .facility("BC Children's Hospital").ward("Pediatric Medicine").room("214").bed("A")
                .attendingPhysician("Dr. Priya Mehta")
                .visitNumber("VN-2024-66003").patientClass("INPATIENT")
                .notes("Admitted from ED after partial response to bronchodilator therapy in ED.").build());

        medRepo.save(Medication.builder()
                .patient(p3).medicationName("Salbutamol (Albuterol)").genericName("salbutamol sulfate")
                .rxnormCode("435").dose("2.5 mg").frequency("Q4H").route("INHALED")
                .status("ACTIVE").prescriber("Dr. Priya Mehta")
                .startDate(LocalDate.now().minusDays(2)).indication("Acute asthma exacerbation - bronchodilation")
                .specialInstructions("Via nebulizer. Reassess breath sounds after each treatment.")
                .visitNumber("VN-2024-66003").build());

        medRepo.save(Medication.builder()
                .patient(p3).medicationName("Prednisolone").genericName("prednisolone")
                .rxnormCode("8638").dose("30 mg").frequency("QD").route("ORAL")
                .status("ACTIVE").prescriber("Dr. Priya Mehta")
                .startDate(LocalDate.now().minusDays(2)).endDate(LocalDate.now().plusDays(3))
                .indication("Acute asthma - systemic corticosteroid").specialInstructions("5-day course. Give with food.")
                .visitNumber("VN-2024-66003").build());

        encRepo.save(Encounter.builder()
                .patient(p3).visitNumber("VN-2024-66003")
                .encounterType("PROGRESS_NOTE")
                .encounterDatetime(LocalDateTime.now().minusDays(1))
                .providerName("Dr. Priya Mehta").providerRole("PHYSICIAN")
                .department("Pediatrics")
                .chiefComplaint("Asthma exacerbation follow-up")
                .subjective("Parents report improved breathing overnight. Aisha sleeping more comfortably. " +
                        "Still some wheeze on exertion. No fever.")
                .objective("SpO2 95% on room air. HR 96. Mild subcostal retractions. Wheeze end-expiratory only.")
                .assessment("Moderate asthma exacerbation, improving. Good response to treatment.")
                .plan("Continue Q4H salbutamol nebs. Oral prednisolone day 2 of 5. " +
                        "If SpO2 stable >96% for 12hrs, can trial salbutamol Q6H. Goal discharge tomorrow.")
                .diagnosisCode("J45.31").diagnosisDescription("Moderate persistent asthma with acute exacerbation")
                .vitalsBp("104/68").vitalsHr(96).vitalsTemp(37.0).vitalsSpo2(95).vitalsWeightKg(38.5)
                .status("FINAL").build());

        // ── Patient 4: Outpatient - prenatal visit ──
        Patient p4 = patientRepo.save(Patient.builder()
                .mrn("MRN-10004")
                .firstName("Fatima")
                .lastName("Al-Rashid")
                .dateOfBirth(LocalDate.of(1990, 5, 22))
                .gender("female")
                .phone("778-555-0412")
                .email("f.alrashid@example.com")
                .addressLine("1900 Lonsdale Avenue")
                .city("North Vancouver")
                .province("BC")
                .postalCode("V7M 2J7")
                .healthCardNumber("BC9006789012")
                .bloodType("AB+")
                .allergies("Latex")
                .primaryLanguage("Arabic")
                .active(true)
                .build());

        adtRepo.save(AdtEvent.builder()
                .patient(p4).eventType("REGISTER").eventCode("A04")
                .eventDatetime(LocalDateTime.now().minusDays(1))
                .facility("Lions Gate Hospital").ward("Obstetrics Outpatient").room("OB-Clinic-3").bed(null)
                .attendingPhysician("Dr. Linda Torres").admittingDiagnosis("Routine prenatal visit - 28 weeks")
                .visitNumber("VN-2024-55004").patientClass("OUTPATIENT")
                .notes("G2P1. 28-week prenatal appointment. GDM screening ordered.").build());

        medRepo.save(Medication.builder()
                .patient(p4).medicationName("Prenatal Multivitamin").genericName("prenatal multivitamin with DHA")
                .rxnormCode("1162459").dose("1 tablet").frequency("QD").route("ORAL")
                .status("ACTIVE").prescriber("Dr. Linda Torres")
                .startDate(LocalDate.now().minusMonths(5)).indication("Prenatal nutritional supplementation")
                .refillsRemaining(2).pharmacy("Shoppers Drug Mart - Lonsdale")
                .visitNumber("VN-2024-55004").build());

        medRepo.save(Medication.builder()
                .patient(p4).medicationName("Ferrous Gluconate").genericName("ferrous gluconate")
                .rxnormCode("8780").dose("300 mg").frequency("BID").route("ORAL")
                .status("ACTIVE").prescriber("Dr. Linda Torres")
                .startDate(LocalDate.now().minusMonths(2)).indication("Iron deficiency anemia in pregnancy")
                .specialInstructions("Take on empty stomach. Avoid with dairy. May cause dark stools.")
                .refillsRemaining(1).pharmacy("Shoppers Drug Mart - Lonsdale")
                .visitNumber("VN-2024-55004").build());

        encRepo.save(Encounter.builder()
                .patient(p4).visitNumber("VN-2024-55004")
                .encounterType("SOAP_NOTE")
                .encounterDatetime(LocalDateTime.now().minusDays(1))
                .providerName("Dr. Linda Torres").providerRole("PHYSICIAN")
                .department("Obstetrics & Gynecology")
                .chiefComplaint("28-week prenatal checkup")
                .subjective("34-year-old G2P1 at 28+2 weeks. Reports mild lower back pain and ankle swelling. " +
                        "Fetal movements felt regularly. No bleeding, no headache, no visual changes.")
                .objective("BP 118/74. Weight 71.2 kg (+1.5 kg since last visit). Fundal height 28 cm. " +
                        "FHR 148 bpm by doppler. Bilateral ankle edema 1+.")
                .assessment("28-week pregnancy, uncomplicated. Physiologic edema. " +
                        "Mild anemia improving on iron supplementation.")
                .plan("GDM screening (2hr OGTT) ordered. Repeat CBC in 4 weeks. Continue prenatal vitamins and iron. " +
                        "Next OB appointment at 32 weeks. Kick counts QD.")
                .diagnosisCode("Z34.28").diagnosisDescription("Encounter for supervision of normal pregnancy, second trimester")
                .vitalsBp("118/74").vitalsHr(80).vitalsTemp(36.7).vitalsSpo2(99).vitalsWeightKg(71.2)
                .status("FINAL").build());

        // ── Seed AllergyIntolerance Records ──
        allergyRepo.save(AllergyIntolerance.builder()
                .patient(p1).display("Allergy to Penicillin").code("297422002")
                .category("medication").criticality("high").clinicalStatus("active").verificationStatus("confirmed")
                .reactionManifestation("Severe Urticaria & Anaphylaxis").reactionSeverity("severe").build());

        allergyRepo.save(AllergyIntolerance.builder()
                .patient(p1).display("Allergy to Sulfa Drugs").code("91936005")
                .category("medication").criticality("low").clinicalStatus("active").verificationStatus("confirmed")
                .reactionManifestation("Mild Maculopapular Rash").reactionSeverity("mild").build());

        allergyRepo.save(AllergyIntolerance.builder()
                .patient(p3).display("Allergy to Egg Protein").code("91930004")
                .category("food").criticality("high").clinicalStatus("active").verificationStatus("confirmed")
                .reactionManifestation("Wheezing & Facial Angioedema").reactionSeverity("severe").build());

        allergyRepo.save(AllergyIntolerance.builder()
                .patient(p4).display("Allergy to Latex").code("300916003")
                .category("environment").criticality("low").clinicalStatus("active").verificationStatus("confirmed")
                .reactionManifestation("Contact Dermatitis").reactionSeverity("moderate").build());

        // ── Seed Observation Records (Labs & Vitals) ──
        // Margaret Chen (p1) — HbA1c & Glucose
        observationRepo.save(Observation.builder()
                .patient(p1).status("final").category("laboratory")
                .code("4548-4").codeSystem("http://loinc.org").codeDisplay("Hemoglobin A1c/Hemoglobin.total in Blood")
                .valueQuantity(9.4).valueUnit("%").interpretation("H")
                .effectiveDateTime(LocalDateTime.now().minusDays(10)).issued(LocalDateTime.now().minusDays(10)).build());

        observationRepo.save(Observation.builder()
                .patient(p1).status("final").category("laboratory")
                .code("2339-0").codeSystem("http://loinc.org").codeDisplay("Glucose [Mass/volume] in Blood")
                .valueQuantity(18.2).valueUnit("mmol/L").interpretation("HH")
                .effectiveDateTime(LocalDateTime.now().minusDays(3)).issued(LocalDateTime.now().minusDays(3)).build());

        // Robert Okafor (p2) — Troponin I & Heart Rate
        observationRepo.save(Observation.builder()
                .patient(p2).status("final").category("laboratory")
                .code("10839-9").codeSystem("http://loinc.org").codeDisplay("Troponin I.cardiac [Mass/volume] in Serum or Plasma")
                .valueQuantity(12.4).valueUnit("ng/mL").interpretation("HH")
                .effectiveDateTime(LocalDateTime.now().minusDays(5)).issued(LocalDateTime.now().minusDays(5)).build());

        observationRepo.save(Observation.builder()
                .patient(p2).status("final").category("vital-signs")
                .code("8867-4").codeSystem("http://loinc.org").codeDisplay("Heart Rate")
                .valueQuantity(92.0).valueUnit("beats/min").interpretation("N")
                .effectiveDateTime(LocalDateTime.now().minusDays(1)).issued(LocalDateTime.now().minusDays(1)).build());

        // Aisha Patel (p3) — Peak Flow Rate & Oxygen Saturation
        observationRepo.save(Observation.builder()
                .patient(p3).status("final").category("vital-signs")
                .code("33452-4").codeSystem("http://loinc.org").codeDisplay("Peak expiratory flow rate")
                .valueQuantity(180.0).valueUnit("L/min").interpretation("L")
                .effectiveDateTime(LocalDateTime.now().minusDays(2)).issued(LocalDateTime.now().minusDays(2)).build());

        observationRepo.save(Observation.builder()
                .patient(p3).status("final").category("vital-signs")
                .code("2708-6").codeSystem("http://loinc.org").codeDisplay("Oxygen saturation in Arterial blood by Pulse oximetry")
                .valueQuantity(94.0).valueUnit("%").interpretation("L")
                .effectiveDateTime(LocalDateTime.now().minusDays(2)).issued(LocalDateTime.now().minusDays(2)).build());

        log.info("Seeded {} patients, {} ADT events, {} medications, {} encounters, {} allergies, {} observations",
                patientRepo.count(), adtRepo.count(), medRepo.count(), encRepo.count(), allergyRepo.count(), observationRepo.count());
    }
}
