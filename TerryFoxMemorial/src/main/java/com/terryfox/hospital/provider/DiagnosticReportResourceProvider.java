package com.terryfox.hospital.provider;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import com.terryfox.hospital.model.GenomicReportEntity;
import com.terryfox.hospital.repository.GenomicReportRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class DiagnosticReportResourceProvider implements IResourceProvider {

    @Autowired
    private GenomicReportRepository reportRepository;

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return DiagnosticReport.class;
    }

    @Read
    public DiagnosticReport read(@IdParam IdType theId) {
        Long id;
        try {
            id = theId.getIdPartAsLong();
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Invalid DiagnosticReport ID: " + theId.getIdPart());
        }

        GenomicReportEntity entity = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DiagnosticReport with ID " + id + " not found."));

        return mapToDiagnosticReport(entity);
    }

    @Search
    public List<DiagnosticReport> search(@OptionalParam(name = DiagnosticReport.SP_SUBJECT) ReferenceParam theSubject) {
        List<DiagnosticReport> list = new ArrayList<>();

        if (theSubject != null) {
            try {
                Long patientId = Long.parseLong(theSubject.getIdPart());
                List<GenomicReportEntity> matches = reportRepository.findByPatientId(patientId);
                for (GenomicReportEntity entity : matches) {
                    list.add(mapToDiagnosticReport(entity));
                }
                return list;
            } catch (NumberFormatException e) {
                List<GenomicReportEntity> matches = reportRepository.findByPatientPhn(theSubject.getIdPart());
                for (GenomicReportEntity entity : matches) {
                    list.add(mapToDiagnosticReport(entity));
                }
                return list;
            }
        }

        for (GenomicReportEntity entity : reportRepository.findAll()) {
            list.add(mapToDiagnosticReport(entity));
        }

        return list;
    }

    public DiagnosticReport mapToDiagnosticReport(GenomicReportEntity entity) {
        DiagnosticReport report = new DiagnosticReport();
        report.setId(new IdType("DiagnosticReport", entity.getId()));
        report.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);

        CodeableConcept category = report.addCategory();
        category.addCoding()
                .setSystem("http://terminology.hl7.org/CodeSystem/v2-0074")
                .setCode("GE")
                .setDisplay("Genetics");

        CodeableConcept code = new CodeableConcept();
        code.setText(entity.getReportTitle() != null ? entity.getReportTitle() : "Genomic Diagnostic Report");
        report.setCode(code);

        report.setSubject(new Reference("Patient/" + entity.getPatient().getId())
                .setDisplay(entity.getPatient().getGivenName() + " " + entity.getPatient().getFamilyName()));

        report.setConclusion(entity.getGeneTarget() + ": " + entity.getMutationResult() + " — " + entity.getInterpretation());

        if (entity.getTestDate() != null) {
            report.setEffective(new DateTimeType(Date.from(entity.getTestDate().atStartOfDay(ZoneId.systemDefault()).toInstant())));
        }

        if (entity.getPathologistName() != null) {
            report.addPerformer(new Reference().setDisplay(entity.getPathologistName()));
        }

        return report;
    }
}
