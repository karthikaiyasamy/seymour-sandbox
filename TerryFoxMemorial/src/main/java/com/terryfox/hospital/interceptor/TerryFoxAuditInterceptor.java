package com.terryfox.hospital.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Interceptor
public class TerryFoxAuditInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TerryFoxAuditInterceptor.class);

    @Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)
    public void logIncomingFhirRequest(RequestDetails theRequestDetails) {
        String action = theRequestDetails.getRestOperationType() != null
                ? theRequestDetails.getRestOperationType().name() : "OPERATION";
        String resourceName = theRequestDetails.getResourceName() != null
                ? theRequestDetails.getResourceName() : "Server";
        
        String clientIp = "127.0.0.1";
        if (theRequestDetails instanceof ServletRequestDetails srd && srd.getServletRequest() != null) {
            clientIp = srd.getServletRequest().getRemoteAddr();
        }

        log.info("[TERRY-FOX-AUDIT] Incoming FHIR Request: HTTP {} {} | Action: {} | Resource: {} | Client IP: {}",
                theRequestDetails.getRequestType(),
                theRequestDetails.getCompleteUrl(),
                action,
                resourceName,
                clientIp);
    }
}
