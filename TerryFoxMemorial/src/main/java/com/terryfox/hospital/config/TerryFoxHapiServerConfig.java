package com.terryfox.hospital.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.terryfox.hospital.interceptor.TerryFoxAuditInterceptor;
import com.terryfox.hospital.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class TerryFoxHapiServerConfig {

    @Autowired
    private PatientResourceProvider patientResourceProvider;

    @Autowired
    private ConditionResourceProvider conditionResourceProvider;

    @Autowired
    private ResearchStudyResourceProvider researchStudyResourceProvider;

    @Autowired
    private ResearchSubjectResourceProvider researchSubjectResourceProvider;

    @Autowired
    private DiagnosticReportResourceProvider diagnosticReportResourceProvider;

    @Autowired
    private TerryFoxAuditInterceptor auditInterceptor;

    @Bean
    public ServletRegistrationBean<RestfulServer> fhirServlet() {
        RestfulServer server = new RestfulServer(FhirContext.forR4());
        server.setServerName("Terry Fox Memorial Hospital - HAPI FHIR R4 Engine");
        server.setServerVersion("1.0.0-ONCOLOGY");
        server.setImplementationDescription("BC Cancer Informatics & Clinical Trials HAPI FHIR Server Sandbox");
        server.setDefaultResponseEncoding(EncodingEnum.JSON);

        server.setResourceProviders(List.of(
                patientResourceProvider,
                conditionResourceProvider,
                researchStudyResourceProvider,
                researchSubjectResourceProvider,
                diagnosticReportResourceProvider
        ));

        server.getInterceptorService().registerInterceptor(auditInterceptor);

        ServletRegistrationBean<RestfulServer> registration = new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("TerryFoxHapiFhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
