package com.terryfox.hospital.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.terryfox.hospital.model.OncologyConditionEntity;
import com.terryfox.hospital.repository.OncologyConditionRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class ConditionResourceProvider implements IResourceProvider {

    @Autowired
    private OncologyConditionRepository conditionRepository;

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return Condition.class;
    }

    @Read
    public Condition read(@IdParam IdType theId) {
        Long id;
        try {
            id = theId.getIdPartAsLong();
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Invalid Condition ID: " + theId.getIdPart());
        }

        OncologyConditionEntity entity = conditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Condition with ID " + id + " not found."));

        return mapToFhirCondition(entity);
    }

    @Search
    public List<Condition> search(
            @OptionalParam(name = Condition.SP_PATIENT) ReferenceParam thePatient,
            @OptionalParam(name = Condition.SP_CLINICAL_STATUS) TokenParam theStatus) {

        List<Condition> result = new ArrayList<>();

        if (thePatient != null) {
            String patientIdStr = thePatient.getIdPart();
            try {
                Long patientId = Long.parseLong(patientIdStr);
                List<OncologyConditionEntity> matches = conditionRepository.findByPatientId(patientId);
                for (OncologyConditionEntity entity : matches) {
                    result.add(mapToFhirCondition(entity));
                }
                return result;
            } catch (NumberFormatException e) {
                List<OncologyConditionEntity> matches = conditionRepository.findByPatientPhn(patientIdStr);
                for (OncologyConditionEntity entity : matches) {
                    result.add(mapToFhirCondition(entity));
                }
                return result;
            }
        }

        for (OncologyConditionEntity entity : conditionRepository.findAll()) {
            result.add(mapToFhirCondition(entity));
        }

        return result;
    }

    public Condition mapToFhirCondition(OncologyConditionEntity entity) {
        Condition condition = new Condition();
        condition.setId(new IdType("Condition", entity.getId()));

        condition.setSubject(new Reference("Patient/" + entity.getPatient().getId())
                .setDisplay(entity.getPatient().getGivenName() + " " + entity.getPatient().getFamilyName()));

        CodeableConcept category = condition.addCategory();
        category.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/condition-category")
                .setCode("problem-list-item")
                .setDisplay("Problem List Item");

        CodeableConcept code = new CodeableConcept();
        code.addCoding()
                .setSystem(entity.getCodeSystem() != null ? entity.getCodeSystem() : "http://snomed.info/sct")
                .setCode(entity.getDiagnosisCode())
                .setDisplay(entity.getDiagnosisDisplay());
        condition.setCode(code);

        CodeableConcept clinicalStatus = new CodeableConcept();
        clinicalStatus.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/condition-clinical")
                .setCode(entity.getClinicalStatus() != null ? entity.getClinicalStatus() : "active");
        condition.setClinicalStatus(clinicalStatus);

        if (entity.getOnsetDate() != null) {
            condition.setOnset(new DateTimeType(Date.from(entity.getOnsetDate().atStartOfDay(ZoneId.systemDefault()).toInstant())));
        }

        if (entity.getTnmStageGroup() != null) {
            Condition.ConditionStageComponent stage = condition.addStage();
            CodeableConcept summary = new CodeableConcept();
            summary.addCoding()
                    .setSystem("http://snomed.info/sct")
                    .setDisplay(entity.getTnmStageGroup());
            stage.setSummary(summary);

            if (entity.getPrimaryTumorCategory() != null) {
                stage.addExtension()
                        .setUrl("http://hl7.org/fhir/us/mcode/StructureDefinition/mcode-tnm-primary-tumor-category")
                        .setValue(new StringType(entity.getPrimaryTumorCategory()));
            }
            if (entity.getRegionalNodesCategory() != null) {
                stage.addExtension()
                        .setUrl("http://hl7.org/fhir/us/mcode/StructureDefinition/mcode-tnm-regional-nodes-category")
                        .setValue(new StringType(entity.getRegionalNodesCategory()));
            }
            if (entity.getDistantMetastasisCategory() != null) {
                stage.addExtension()
                        .setUrl("http://hl7.org/fhir/us/mcode/StructureDefinition/mcode-tnm-distant-metastasis-category")
                        .setValue(new StringType(entity.getDistantMetastasisCategory()));
            }
        }

        return condition;
    }
}
