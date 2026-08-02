package com.terryfox.hospital.provider;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.terryfox.hospital.model.PatientEntity;
import com.terryfox.hospital.repository.PatientRepository;
import com.terryfox.hospital.util.PhnValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class PatientResourceProvider implements IResourceProvider {

    private static final String BC_PHN_SYSTEM = "http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn";
    private static final String TERRYFOX_MRN_SYSTEM = "http://terryfox.hospital/mrn";

    @Autowired
    private PatientRepository patientRepository;

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType theId) {
        Long id;
        try {
            id = theId.getIdPartAsLong();
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Invalid Patient ID: " + theId.getIdPart());
        }

        PatientEntity entity = patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient with ID " + id + " not found."));

        return mapToFhirPatient(entity);
    }

    @Search
    public List<Patient> search(
            @OptionalParam(name = Patient.SP_IDENTIFIER) TokenParam theIdentifier,
            @OptionalParam(name = Patient.SP_FAMILY) StringParam theFamily) {

        List<Patient> result = new ArrayList<>();

        if (theIdentifier != null) {
            String value = theIdentifier.getValue();
            Optional<PatientEntity> match = patientRepository.findByPhn(value);
            if (match.isEmpty()) {
                match = patientRepository.findByMrn(value);
            }
            match.ifPresent(p -> result.add(mapToFhirPatient(p)));
            return result;
        }

        if (theFamily != null) {
            List<PatientEntity> matches = patientRepository.findByFamilyNameIgnoreCase(theFamily.getValue());
            for (PatientEntity p : matches) {
                result.add(mapToFhirPatient(p));
            }
            return result;
        }

        for (PatientEntity p : patientRepository.findAll()) {
            result.add(mapToFhirPatient(p));
        }

        return result;
    }

    @Create
    public MethodOutcome create(@ResourceParam Patient thePatient) {
        String phn = null;
        String mrn = null;

        for (Identifier id : thePatient.getIdentifier()) {
            if (BC_PHN_SYSTEM.equals(id.getSystem())) {
                phn = id.getValue();
            } else if (TERRYFOX_MRN_SYSTEM.equals(id.getSystem())) {
                mrn = id.getValue();
            }
        }

        if (phn != null && !PhnValidator.isValidPhn(phn)) {
            throw new InvalidRequestException("Invalid British Columbia PHN Modulus-11 Checksum: " + PhnValidator.maskPhn(phn));
        }

        String family = thePatient.hasName() ? thePatient.getNameFirstRep().getFamily() : "Unknown";
        String given = (thePatient.hasName() && !thePatient.getNameFirstRep().getGiven().isEmpty())
                ? thePatient.getNameFirstRep().getGiven().get(0).getValue() : "Unknown";

        PatientEntity entity = PatientEntity.builder()
                .phn(phn != null ? phn : "9" + (System.currentTimeMillis() % 1000000000L))
                .mrn(mrn != null ? mrn : "TF-" + System.currentTimeMillis() % 10000L)
                .givenName(given)
                .familyName(family)
                .gender(thePatient.hasGender() ? thePatient.getGender().toCode() : "unknown")
                .build();

        PatientEntity saved = patientRepository.save(entity);

        MethodOutcome outcome = new MethodOutcome();
        outcome.setCreated(true);
        outcome.setId(new IdType("Patient", saved.getId()));
        outcome.setResource(mapToFhirPatient(saved));
        return outcome;
    }

    @Operation(name = "$match", type = Patient.class)
    public Bundle match(@ResourceParam Bundle theRequestBundle) {
        Bundle response = new Bundle();
        response.setType(Bundle.BundleType.SEARCHSET);

        for (Bundle.BundleEntryComponent entry : theRequestBundle.getEntry()) {
            if (entry.getResource() instanceof Patient p) {
                for (Identifier id : p.getIdentifier()) {
                    Optional<PatientEntity> found = patientRepository.findByPhn(id.getValue());
                    if (found.isPresent()) {
                        Bundle.BundleEntryComponent resEntry = response.addEntry();
                        resEntry.setResource(mapToFhirPatient(found.get()));
                        resEntry.setSearch(new Bundle.BundleEntrySearchComponent().setScore(1.0));
                    }
                }
            }
        }

        return response;
    }

    public Patient mapToFhirPatient(PatientEntity entity) {
        Patient fhirPatient = new Patient();
        fhirPatient.setId(new IdType("Patient", entity.getId()));

        fhirPatient.addIdentifier()
                .setSystem(BC_PHN_SYSTEM)
                .setValue(entity.getPhn())
                .setUse(Identifier.IdentifierUse.OFFICIAL);

        fhirPatient.addIdentifier()
                .setSystem(TERRYFOX_MRN_SYSTEM)
                .setValue(entity.getMrn())
                .setUse(Identifier.IdentifierUse.USUAL);

        HumanName name = fhirPatient.addName();
        name.setFamily(entity.getFamilyName());
        name.addGiven(entity.getGivenName());

        if ("male".equalsIgnoreCase(entity.getGender())) {
            fhirPatient.setGender(Enumerations.AdministrativeGender.MALE);
        } else if ("female".equalsIgnoreCase(entity.getGender())) {
            fhirPatient.setGender(Enumerations.AdministrativeGender.FEMALE);
        } else {
            fhirPatient.setGender(Enumerations.AdministrativeGender.UNKNOWN);
        }

        if (entity.getBirthDate() != null) {
            fhirPatient.setBirthDate(Date.from(entity.getBirthDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }

        if (entity.getAddressLine() != null) {
            Address address = fhirPatient.addAddress();
            address.addLine(entity.getAddressLine());
            address.setCity(entity.getCity());
            address.setState(entity.getState());
            address.setPostalCode(entity.getPostalCode());
        }

        if (entity.getPrimaryOncologist() != null) {
            fhirPatient.addExtension()
                    .setUrl("http://terryfox.hospital/fhir/StructureDefinition/primary-oncologist")
                    .setValue(new StringType(entity.getPrimaryOncologist()));
        }

        return fhirPatient;
    }
}
