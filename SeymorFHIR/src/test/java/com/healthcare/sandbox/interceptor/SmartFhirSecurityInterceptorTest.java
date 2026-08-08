package com.healthcare.sandbox.interceptor;

import com.healthcare.sandbox.service.TokenStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class SmartFhirSecurityInterceptorTest {

    private TokenStoreService tokenStoreService;
    private com.healthcare.sandbox.service.JwtKeyService jwtKeyService;
    private SmartFhirSecurityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenStoreService = new TokenStoreService();
        jwtKeyService = new com.healthcare.sandbox.service.JwtKeyService();
        jwtKeyService.init();
        interceptor = new SmartFhirSecurityInterceptor(tokenStoreService, jwtKeyService);
    }

    @Test
    @DisplayName("Should allow request with valid registered Bearer token")
    void testAllowValidToken() throws Exception {
        String token = "eySmartFhirToken_test_123";
        tokenStoreService.registerToken(token, "1", "client_app", 3600);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fhir/Patient");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertTrue(allowed, "Interceptor should allow request with valid registered token");
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Should block request with unregistered/fake token returning 401 OperationOutcome")
    void testBlockFakeToken() throws Exception {
        String fakeToken = "eySmartFhirToken_fake_999";

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fhir/Patient");
        request.addHeader("Authorization", "Bearer " + fakeToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed, "Interceptor must block unregistered/fake token");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("OperationOutcome"));
    }

    @Test
    @DisplayName("Should block request with missing Authorization header")
    void testBlockMissingAuthHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fhir/Patient");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertFalse(allowed, "Interceptor must block requests missing Authorization header");
        assertEquals(401, response.getStatus());
    }
}
