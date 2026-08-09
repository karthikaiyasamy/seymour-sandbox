package com.terryfox.hospital.provider;

import ca.uhn.fhir.rest.param.TokenParam;
import com.terryfox.hospital.model.PatientEntity;
import com.terryfox.hospital.repository.PatientRepository;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientResourceProviderTest {

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private PatientResourceProvider provider;

    private PatientEntity margaret;
    private PatientEntity sarah;

    @BeforeEach
    void setUp() {
        margaret = PatientEntity.builder()
                .id(1L)
                .mrn("MRN-10001")
                .phn("BC9001234567")
                .givenName("Margaret")
                .familyName("Chen")
                .gender("female")
                .birthDate(LocalDate.of(1948, 3, 12))
                .build();

        sarah = PatientEntity.builder()
                .id(2L)
                .mrn("MRN-10002")
                .phn("9234567897")
                .givenName("Sarah")
                .familyName("Jenkins")
                .gender("female")
                .birthDate(LocalDate.of(1976, 11, 23))
                .build();
    }

    @Test
    @DisplayName("Should retrieve synthetic Margaret Chen oncology patient by HAPI FHIR IdType @Read")
    void testGetPatientById_MargaretChen() {
        when(patientRepository.findById(1L)).thenReturn(Optional.of(margaret));

        Patient patient = provider.read(new IdType(1L));

        assertNotNull(patient);
        assertEquals("Margaret", patient.getNameFirstRep().getGivenAsSingleString());
        assertEquals("Chen", patient.getNameFirstRep().getFamily());
        assertEquals("1948-03-12", patient.getBirthDateElement().getValueAsString());

        boolean hasPhn = patient.getIdentifier().stream()
                .anyMatch(id -> "BC9001234567".equals(id.getValue()));
        assertTrue(hasPhn, "Patient 1 must contain BC PHN BC9001234567");
    }

    @Test
    @DisplayName("Should search oncology patient roster by PHN identifier TokenParam @Search")
    void testSearch_ByIdentifierPhn() {
        when(patientRepository.findByPhn("9234567897")).thenReturn(Optional.of(sarah));

        TokenParam tokenParam = new TokenParam().setValue("9234567897");
        List<Patient> results = provider.search(tokenParam, null);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Jenkins", results.get(0).getNameFirstRep().getFamily());
    }

    @Test
    @DisplayName("Should search oncology patient roster by MRN-10001 TokenParam @Search")
    void testSearch_ByMrn() {
        when(patientRepository.findByPhn("MRN-10001")).thenReturn(Optional.empty());
        when(patientRepository.findByMrn("MRN-10001")).thenReturn(Optional.of(margaret));

        TokenParam tokenParam = new TokenParam().setValue("MRN-10001");
        List<Patient> results = provider.search(tokenParam, null);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Chen", results.get(0).getNameFirstRep().getFamily());
    }

    @Test
    @DisplayName("Should return all seeded oncology patients when search parameter is null")
    void testSearch_AllPatients() {
        when(patientRepository.findAll()).thenReturn(List.of(margaret, sarah));

        List<Patient> results = provider.search(null, null);

        assertNotNull(results);
        assertEquals(2, results.size());
    }
}
