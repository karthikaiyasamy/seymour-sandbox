package com.healthcare.sandbox.service;

import com.healthcare.sandbox.model.AdtEvent;
import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.healthcare.sandbox.model.Hl7AuditLog;
import com.healthcare.sandbox.repository.Hl7AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.healthcare.sandbox.model.PatientMatchReview;
import com.healthcare.sandbox.repository.PatientMatchReviewRepository;

class Hl7ServiceTest {

    private PatientRepository patientRepo;
    private Hl7AuditLogRepository auditLogRepo;
    private PatientMatchReviewRepository matchReviewRepo;
    private Hl7Service hl7Service;

    @BeforeEach
    void setUp() {
        patientRepo = mock(PatientRepository.class);
        auditLogRepo = mock(Hl7AuditLogRepository.class);
        matchReviewRepo = mock(PatientMatchReviewRepository.class);
        when(auditLogRepo.save(any(Hl7AuditLog.class))).thenAnswer(i -> i.getArguments()[0]);
        when(matchReviewRepo.save(any(PatientMatchReview.class))).thenAnswer(i -> i.getArguments()[0]);
        hl7Service = new Hl7Service(patientRepo, auditLogRepo, matchReviewRepo);
    }

    @Test
    @DisplayName("Should generate valid HL7 v2.4 ADT message from AdtEvent entity")
    void testGenerateHl7AdtMessage() {
        Patient patient = Patient.builder()
                .id(1L)
                .mrn("MRN-TEST-100")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .gender("male")
                .healthCardNumber("9000000071")
                .city("Vancouver")
                .province("BC")
                .postalCode("V5K 1A1")
                .phone("604-555-0199")
                .build();

        AdtEvent adtEvent = AdtEvent.builder()
                .id(10L)
                .patient(patient)
                .eventCode("A01")
                .eventType("ADMIT")
                .eventDatetime(LocalDateTime.of(2026, 8, 2, 10, 30, 0))
                .facility("Vancouver General Hospital")
                .ward("4 North")
                .room("412")
                .bed("A")
                .attendingPhysician("Dr. Sarah Park")
                .visitNumber("VN-2026-901")
                .patientClass("INPATIENT")
                .build();

        String rawHl7 = hl7Service.generateHl7(adtEvent);

        assertNotNull(rawHl7, "Generated HL7 message should not be null");
        assertTrue(rawHl7.contains("MSH|^~\\&|SANDBOX_EHR|Vancouver General Hospital"));
        assertTrue(rawHl7.contains("ADT^A01^ADT_A01"));
        assertTrue(rawHl7.contains("PID|1||MRN-TEST-100^^^MRN||Doe^John||19850615|M"));
        assertTrue(rawHl7.contains("PV1|1|I|4 North^412^A"));
    }

    @Test
    @DisplayName("Should reject duplicate MSH-10 Message Control ID idempotently")
    void testHl7DuplicateIdempotencyRejection() {
        String hl7Text = "MSH|^~\\&|SANDBOX_EHR|VGH|REC_APP|REC_FAC|20260802103000||ADT^A01^ADT_A01|MSG-9001|P|2.4\nPID|1||MRN-101^^^MRN||Smith^Jane";
        
        when(auditLogRepo.findByMessageControlId("MSG-9001"))
                .thenReturn(Optional.of(Hl7AuditLog.builder().messageControlId("MSG-9001").correlationId("corr-123").status("DELIVERED").build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> hl7Service.parseHl7(hl7Text));
        assertTrue(ex.getMessage().contains("Duplicate MSH-10 Message Control ID rejected"));
    }
}
