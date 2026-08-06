package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.model.Patient;
import com.healthcare.sandbox.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PatientControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PatientRepository patientRepo;

    @InjectMocks
    private PatientController patientController;

    private Patient testPatient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(patientController).build();

        testPatient = Patient.builder()
                .id(1L)
                .mrn("MRN-10001")
                .firstName("Margaret")
                .lastName("Chen")
                .gender("female")
                .dateOfBirth(LocalDate.of(1948, 3, 12))
                .healthCardNumber("9000000071") // Valid BC PHN
                .active(true)
                .build();
    }

    @Test
    @DisplayName("GET /api/fhir/Patient - Should return FHIR searchset Bundle with active patients")
    void testGetAllPatients() throws Exception {
        when(patientRepo.findByActiveTrue()).thenReturn(List.of(testPatient));

        mockMvc.perform(get("/api/fhir/Patient"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType", is("Bundle")))
                .andExpect(jsonPath("$.type", is("searchset")))
                .andExpect(jsonPath("$.entry", hasSize(1)))
                .andExpect(jsonPath("$.entry[0].resource.name[0].family", is("Chen")))
                .andExpect(jsonPath("$.entry[0].resource.name[0].given[0]", is("Margaret")));

        verify(patientRepo, times(1)).findByActiveTrue();
    }

    @Test
    @DisplayName("GET /api/fhir/Patient/{id} - Should return single Patient FHIR Resource when found by ID")
    void testGetPatientByIdSuccess() throws Exception {
        when(patientRepo.findById(1L)).thenReturn(Optional.of(testPatient));

        mockMvc.perform(get("/api/fhir/Patient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType", is("Patient")))
                .andExpect(jsonPath("$.id", is("1")))
                .andExpect(jsonPath("$.name[0].family", is("Chen")));

        verify(patientRepo, times(1)).findById(1L);
    }

    @Test
    @DisplayName("GET /api/fhir/Patient/{id} - Should return 404 OperationOutcome when patient not found")
    void testGetPatientNotFound() throws Exception {
        when(patientRepo.findById(99L)).thenReturn(Optional.empty());
        when(patientRepo.findByActiveTrue()).thenReturn(List.of());

        mockMvc.perform(get("/api/fhir/Patient/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resourceType", is("OperationOutcome")))
                .andExpect(jsonPath("$.issue[0].code", is("not-found")));
    }

    @Test
    @DisplayName("POST /api/fhir/Patient - Should create patient with valid BC PHN")
    void testCreatePatientSuccess() throws Exception {
        when(patientRepo.save(any(Patient.class))).thenReturn(testPatient);

        String jsonPayload = """
            {
              "mrn": "MRN-10001",
              "firstName": "Margaret",
              "lastName": "Chen",
              "healthCardNumber": "9000000071",
              "gender": "female",
              "dateOfBirth": "1948-03-12",
              "active": true
            }
            """;

        mockMvc.perform(post("/api/fhir/Patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType", is("Patient")))
                .andExpect(jsonPath("$.name[0].family", is("Chen")));

        verify(patientRepo, times(1)).save(any(Patient.class));
    }

    @Test
    @DisplayName("POST /api/fhir/Patient - Should reject patient with invalid BC PHN Modulus-11 checksum")
    void testCreatePatientInvalidPhnRejection() throws Exception {
        String invalidPhnPayload = """
            {
              "mrn": "MRN-99999",
              "firstName": "Invalid",
              "lastName": "User",
              "healthCardNumber": "9234567899",
              "gender": "male",
              "active": true
            }
            """;

        mockMvc.perform(post("/api/fhir/Patient")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPhnPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resourceType", is("OperationOutcome")))
                .andExpect(jsonPath("$.issue[0].diagnostics", containsString("Invalid British Columbia PHN format or checksum")));

        verify(patientRepo, never()).save(any(Patient.class));
    }
}
