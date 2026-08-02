package com.terryfox.hospital.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
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
public class ResearchStudyResourceProvider implements IResourceProvider {

    @Autowired
    private ClinicalTrialRepository trialRepository;

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return ResearchStudy.class;
    }

    @Read
    public ResearchStudy read(@IdParam IdType theId) {
        Long id;
        try {
            id = theId.getIdPartAsLong();
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Invalid ResearchStudy ID: " + theId.getIdPart());
        }

        ClinicalTrialEntity entity = trialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ResearchStudy with ID " + id + " not found."));

        return mapToResearchStudy(entity);
    }

    @Search
    public List<ResearchStudy> search() {
        List<ResearchStudy> list = new ArrayList<>();
        for (ClinicalTrialEntity entity : trialRepository.findAll()) {
            list.add(mapToResearchStudy(entity));
        }
        return list;
    }

    private ResearchStudy mapToResearchStudy(ClinicalTrialEntity entity) {
        ResearchStudy study = new ResearchStudy();
        study.setId(new IdType("ResearchStudy", entity.getId()));
        study.setTitle(entity.getTitle());
        study.setStatus(ResearchStudy.ResearchStudyStatus.ACTIVE);

        study.addIdentifier()
                .setSystem("https://clinicaltrials.gov")
                .setValue(entity.getNctId());

        if (entity.getSponsor() != null) {
            study.setSponsor(new Reference().setDisplay(entity.getSponsor()));
        }

        return study;
    }
}
