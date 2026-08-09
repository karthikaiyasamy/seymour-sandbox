package com.terryfox.hospital.interceptor;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails;
import com.nimbusds.jwt.JWTClaimsSet;
import com.terryfox.hospital.service.TerryFoxJwksKeyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TerryFoxSecurityInterceptorTest {

    @Mock
    private TerryFoxJwksKeyService jwksKeyService;

    @Mock
    private ServletRequestDetails requestDetails;

    @Mock
    private HttpServletRequest servletRequest;

    private TerryFoxSecurityInterceptor securityInterceptor;

    @BeforeEach
    void setUp() {
        securityInterceptor = new TerryFoxSecurityInterceptor(jwksKeyService);
    }

    @Test
    @DisplayName("Should bypass authentication for CORS OPTIONS pre-flight requests")
    void testIncomingRequest_OptionsBypass() {
        when(requestDetails.getRequestType()).thenReturn(RequestTypeEnum.OPTIONS);

        assertDoesNotThrow(() -> securityInterceptor.incomingRequestPreHandled(requestDetails));
        verifyNoInteractions(jwksKeyService);
    }

    @Test
    @DisplayName("Should throw HAPI AuthenticationException (HTTP 401) when Authorization header is missing")
    void testIncomingRequest_MissingAuthorizationHeader() {
        when(requestDetails.getRequestType()).thenReturn(RequestTypeEnum.GET);
        when(requestDetails.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("Authorization")).thenReturn(null);

        AuthenticationException ex = assertThrows(AuthenticationException.class, () ->
                securityInterceptor.incomingRequestPreHandled(requestDetails));

        assertTrue(ex.getMessage().contains("Missing or invalid Bearer Authorization header"));
    }

    @Test
    @DisplayName("Should throw HAPI AuthenticationException when Bearer JWT verification fails")
    void testIncomingRequest_InvalidBearerToken() {
        when(requestDetails.getRequestType()).thenReturn(RequestTypeEnum.GET);
        when(requestDetails.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer INVALID_JWT_TOKEN");
        when(jwksKeyService.verifySignedJwt("INVALID_JWT_TOKEN")).thenReturn(null);

        assertThrows(AuthenticationException.class, () ->
                securityInterceptor.incomingRequestPreHandled(requestDetails));
    }

    @Test
    @DisplayName("Should grant access when Bearer token is statistically verified by JWKS service")
    void testIncomingRequest_Success() throws Exception {
        when(requestDetails.getRequestType()).thenReturn(RequestTypeEnum.GET);
        when(requestDetails.getServletRequest()).thenReturn(servletRequest);
        when(servletRequest.getHeader("Authorization")).thenReturn("Bearer VALID_RS256_TOKEN");

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("seymour_smart_app")
                .claim("client_id", "seymour_smart_app")
                .build();
        when(jwksKeyService.verifySignedJwt("VALID_RS256_TOKEN")).thenReturn(claims);

        assertDoesNotThrow(() -> securityInterceptor.incomingRequestPreHandled(requestDetails));
        verify(jwksKeyService).verifySignedJwt("VALID_RS256_TOKEN");
    }
}
