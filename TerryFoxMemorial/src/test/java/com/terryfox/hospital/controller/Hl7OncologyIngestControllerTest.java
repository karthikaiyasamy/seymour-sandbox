package com.terryfox.hospital.controller;

import com.terryfox.hospital.model.GenomicReportEntity;
import com.terryfox.hospital.model.PatientEntity;
import com.terryfox.hospital.repository.GenomicReportRepository;
import com.terryfox.hospital.repository.PatientRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Hl7OncologyIngestControllerTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private GenomicReportRepository genomicRepository;

    @InjectMocks
    private Hl7OncologyIngestController controller;

    private PatientEntity sarah;
    private GenomicReportEntity report;

    @BeforeEach
    void setUp() {
        sarah = PatientEntity.builder()
                .id(2L)
                .phn("9234567897")
                .mrn("MRN-10002")
                .givenName("Sarah")
                .familyName("Jenkins")
                .build();

        report = GenomicReportEntity.builder()
                .id(101L)
                .patient(sarah)
                .reportTitle("HL7 Molecular Diagnostic Report")
                .build();
    }

    @Test
    @DisplayName("Should successfully parse raw HL7 ORU^R01 pathology payload and register genomic report")
    void testIngestHl7Message_Success() {
        String hl7Payload = """
                MSH|^~\\&|BC_CANCER_LAB|VANCOUVER_CENTER|TERRY_FOX|MAIN_FACILITY|20260808120000||ORU^R01^ORU_R01|MSG-PATH-9001|P|2.4
                PID|1||9234567897^^^PHN||Jenkins^Sarah||19761123|F
                OBR|1|ORD-2026-88|LAB-9901|21008-9^Genomic Pathology Panel^LN|||20260808113000
                OBX|1|TX|EGFR-01^EGFR Mutation Analysis||EGFR Exon 19 Deletion Detected (Pathogenic)||F
                """;

        when(patientRepository.findByPhn("9234567897")).thenReturn(Optional.of(sarah));
        when(genomicRepository.save(any(GenomicReportEntity.class))).thenReturn(report);

        ResponseEntity<Map<String, Object>> response = controller.ingestHl7Message(hl7Payload);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertEquals("DiagnosticReport/101", response.getBody().get("fhirReference"));
        verify(genomicRepository).save(any(GenomicReportEntity.class));
    }

    @Test
    @DisplayName("Should reject HL7 payload with invalid BC PHN Modulus-11 checksum")
    void testIngestHl7Message_InvalidPhnChecksum() {
        String invalidHl7 = """
                MSH|^~\\&|BC_CANCER_LAB|VANCOUVER_CENTER|TERRY_FOX|MAIN_FACILITY|20260808120000||ORU^R01^ORU_R01|MSG-PATH-9002|P|2.4
                PID|1||9234567899^^^PHN||Smith^John||19850615|M
                """;

        ResponseEntity<Map<String, Object>> response = controller.ingestHl7Message(invalidHl7);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("REJECTED", response.getBody().get("status"));
        assertTrue(((String) response.getBody().get("error")).contains("Invalid BC PHN Modulus-11 checksum"));
        verifyNoInteractions(genomicRepository);
    }
}
