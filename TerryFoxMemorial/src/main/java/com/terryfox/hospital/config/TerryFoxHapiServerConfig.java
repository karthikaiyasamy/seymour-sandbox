package com.terryfox.hospital.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import com.terryfox.hospital.interceptor.TerryFoxAuditInterceptor;
import com.terryfox.hospital.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class TerryFoxHapiServerConfig {

    private final PatientResourceProvider patientResourceProvider;
    private final ConditionResourceProvider conditionResourceProvider;
    private final ResearchStudyResourceProvider researchStudyResourceProvider;
    private final ResearchSubjectResourceProvider researchSubjectResourceProvider;
    private final DiagnosticReportResourceProvider diagnosticReportResourceProvider;
    private final TerryFoxAuditInterceptor auditInterceptor;
    private final com.terryfox.hospital.interceptor.TerryFoxSecurityInterceptor securityInterceptor;

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

        org.springframework.web.cors.CorsConfiguration corsConfig = new org.springframework.web.cors.CorsConfiguration();
        corsConfig.addAllowedHeader("*");
        corsConfig.addAllowedMethod("*");
        corsConfig.addAllowedOriginPattern("*");
        corsConfig.setAllowCredentials(false);
        ca.uhn.fhir.rest.server.interceptor.CorsInterceptor corsInterceptor = new ca.uhn.fhir.rest.server.interceptor.CorsInterceptor(corsConfig);
        server.registerInterceptor(corsInterceptor);

        server.getInterceptorService().registerInterceptor(auditInterceptor);
        server.getInterceptorService().registerInterceptor(securityInterceptor);

        ServletRegistrationBean<RestfulServer> registration = new ServletRegistrationBean<>(server, "/fhir/*");
        registration.setName("TerryFoxHapiFhirServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
