package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.PatientRepository;
import com.healthcare.sandbox.service.Hl7Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PediatricControllerTest {

    private MockMvc mockMvc;

    @Mock private PatientRepository patientRepo;
    @Mock private Hl7Service hl7Service;

    @InjectMocks
    private PediatricController pediatricController;

    private Patient testPatient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(pediatricController).build();

        testPatient = Patient.builder()
                .id(1L)
                .mrn("MRN-10001")
                .firstName("Leo")
                .lastName("Vance")
                .healthCardNumber("9000000071")
                .build();
    }

    @Test
    @DisplayName("POST /api/pediatric/vaccine - Should generate VXU HL7 v2 message for valid patient")
    void testSubmitVaccineSuccess() throws Exception {
        when(patientRepo.findById(1L)).thenReturn(Optional.of(testPatient));
        when(hl7Service.generateVxu(eq(testPatient), eq("MMR"), eq("Measles, Mumps, Rubella"), anyString(), eq("LOT-998")))
                .thenReturn("MSH|^~\\&|SEYMOUR|HEALTH|MIRTH|CONNECT|20260805||VXU^V04^VXU_V04|12345|P|2.5.1");

        String payload = """
            {
              "patientId": 1,
              "vaccineCode": "MMR",
              "vaccineName": "Measles, Mumps, Rubella",
              "date": "2026-08-05",
              "lotNumber": "LOT-998"
            }
            """;

        mockMvc.perform(post("/api/pediatric/vaccine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("Vaccine HL7 successfully transmitted to Mirth!")));

        verify(hl7Service, times(1)).generateVxu(eq(testPatient), eq("MMR"), eq("Measles, Mumps, Rubella"), anyString(), eq("LOT-998"));
    }

    @Test
    @DisplayName("POST /api/pediatric/lab - Should generate ORU HL7 v2 message for valid patient")
    void testSubmitLabSuccess() throws Exception {
        when(patientRepo.findById(1L)).thenReturn(Optional.of(testPatient));
        when(hl7Service.generateOru(eq(testPatient), eq("2345-7"), eq("Glucose"), eq("5.4"), eq("mmol/L"), eq("N"), anyString()))
                .thenReturn("MSH|^~\\&|SEYMOUR|HEALTH|MIRTH|CONNECT|20260805||ORU^R01^ORU_R01|12346|P|2.5.1");

        String payload = """
            {
              "patientId": 1,
              "testCode": "2345-7",
              "testName": "Glucose",
              "value": "5.4",
              "unit": "mmol/L",
              "flag": "N",
              "date": "2026-08-05"
            }
            """;

        mockMvc.perform(post("/api/pediatric/lab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.message", is("Lab result HL7 successfully transmitted to Mirth!")));

        verify(hl7Service, times(1)).generateOru(eq(testPatient), eq("2345-7"), eq("Glucose"), eq("5.4"), eq("mmol/L"), eq("N"), anyString());
    }
}
