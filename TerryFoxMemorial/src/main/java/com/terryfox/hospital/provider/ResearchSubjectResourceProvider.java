package com.terryfox.hospital.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.terryfox.hospital.model.ClinicalTrialEntity;
import com.terryfox.hospital.repository.ClinicalTrialRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ResearchSubjectResourceProvider implements IResourceProvider {

    @Autowired
    private ClinicalTrialRepository trialRepository;

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return ResearchSubject.class;
    }

    @Read
    public ResearchSubject read(@IdParam IdType theId) {
        Long id;
        try {
            id = theId.getIdPartAsLong();
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Invalid ResearchSubject ID: " + theId.getIdPart());
        }

        ClinicalTrialEntity entity = trialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ResearchSubject with ID " + id + " not found."));

        return mapToResearchSubject(entity);
    }

    @Search
    public List<ResearchSubject> search(@OptionalParam(name = ResearchSubject.SP_INDIVIDUAL) ReferenceParam theIndividual) {
        List<ResearchSubject> list = new ArrayList<>();

        if (theIndividual != null) {
            try {
                Long patientId = Long.parseLong(theIndividual.getIdPart());
                List<ClinicalTrialEntity> matches = trialRepository.findByPatientId(patientId);
                for (ClinicalTrialEntity entity : matches) {
                    list.add(mapToResearchSubject(entity));
                }
                return list;
            } catch (NumberFormatException e) {
                List<ClinicalTrialEntity> matches = trialRepository.findByPatientPhn(theIndividual.getIdPart());
                for (ClinicalTrialEntity entity : matches) {
                    list.add(mapToResearchSubject(entity));
                }
                return list;
            }
        }

        for (ClinicalTrialEntity entity : trialRepository.findAll()) {
            list.add(mapToResearchSubject(entity));
        }

        return list;
    }

    private ResearchSubject mapToResearchSubject(ClinicalTrialEntity entity) {
        ResearchSubject subject = new ResearchSubject();
        subject.setId(new IdType("ResearchSubject", entity.getId()));
        subject.setStatus(ResearchSubject.ResearchSubjectStatus.ONSTUDY);

        subject.setStudy(new Reference("ResearchStudy/" + entity.getId()).setDisplay(entity.getTitle() + " (" + entity.getNctId() + ")"));

        subject.setIndividual(new Reference("Patient/" + entity.getPatient().getId())
                .setDisplay(entity.getPatient().getGivenName() + " " + entity.getPatient().getFamilyName()));

        if (entity.getAssignedArm() != null) {
            subject.setAssignedArm(entity.getAssignedArm());
        }

        return subject;
    }
}
