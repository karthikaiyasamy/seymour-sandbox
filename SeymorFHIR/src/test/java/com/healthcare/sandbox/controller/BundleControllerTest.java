package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;

import com.healthcare.sandbox.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BundleControllerTest {

    private MockMvc mockMvc;

    @Mock private PatientRepository patientRepo;
    @Mock private ObservationRepository observationRepo;
    @Mock private AllergyIntoleranceRepository allergyRepo;
    @Mock private MedicationRepository medicationRepo;
    @Mock private EncounterRepository encounterRepo;

    @InjectMocks
    private BundleController bundleController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(bundleController).build();
    }

    @Test
    @DisplayName("POST /api/fhir - Should process valid FHIR Transaction Bundle successfully")
    void testProcessValidTransactionBundle() throws Exception {
        Patient savedPatient = Patient.builder().id(101L).mrn("MRN-901").firstName("Eleanor").lastName("Vance").build();
        when(patientRepo.save(any(Patient.class))).thenReturn(savedPatient);

        String bundleJson = """
            {
              "resourceType": "Bundle",
              "type": "transaction",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "mrn": "MRN-901",
                    "firstName": "Eleanor",
                    "lastName": "Vance",
                    "healthCardNumber": "9000000071"
                  },
                  "request": {
                    "method": "POST",
                    "url": "Patient"
                  }
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/fhir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType", is("Bundle")))
                .andExpect(jsonPath("$.type", is("transaction-response")))
                .andExpect(jsonPath("$.entry[0].response.status", is("201 Created")))
                .andExpect(jsonPath("$.entry[0].response.location", is("Patient/101")));

        verify(patientRepo, times(1)).save(any(Patient.class));
    }

    @Test
    @DisplayName("POST /api/fhir - Should reject request if root resourceType is not Bundle")
    void testRejectNonBundleResource() throws Exception {
        String invalidJson = """
            {
              "resourceType": "Patient",
              "id": "1"
            }
            """;

        mockMvc.perform(post("/api/fhir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resourceType", is("OperationOutcome")));
    }

    @Test
    @DisplayName("POST /api/fhir - Should abort transaction and return FHIR OperationOutcome on entry error")
    void testTransactionBundleEntryErrorRejection() throws Exception {
        when(patientRepo.save(any(Patient.class))).thenThrow(new IllegalArgumentException("Invalid British Columbia PHN format or checksum."));

        String bundleJson = """
            {
              "resourceType": "Bundle",
              "type": "transaction",
              "entry": [
                {
                  "resource": {
                    "resourceType": "Patient",
                    "mrn": "MRN-ERR",
                    "firstName": "Invalid",
                    "lastName": "PHN",
                    "healthCardNumber": "9234567899"
                  },
                  "request": {
                    "method": "POST",
                    "url": "Patient"
                  }
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/fhir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bundleJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resourceType", is("OperationOutcome")))
                .andExpect(jsonPath("$.issue[0].diagnostics", containsString("FHIR Bundle Transaction aborted due to entry error")));
    }
}
