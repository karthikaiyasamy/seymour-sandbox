package com.healthcare.sandbox.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenStoreServiceTest {

    private TokenStoreService tokenStoreService;

    @BeforeEach
    void setUp() {
        tokenStoreService = new TokenStoreService();
    }

    @Test
    @DisplayName("Should register token and validate successfully")
    void testValidTokenRegistration() {
        String token = "eySmartFhirToken_valid_12345";
        tokenStoreService.registerToken(token, "1", "seymour_smart_app", 3600);

        assertTrue(tokenStoreService.isValidToken(token), "Token registered in TokenStoreService should be valid");
        assertNotNull(tokenStoreService.getTokenMetadata(token));
        assertEquals("1", tokenStoreService.getTokenMetadata(token).patientId());
    }

    @Test
    @DisplayName("Should reject unregistered/fake token")
    void testFakeTokenRejection() {
        String fakeToken = "eySmartFhirToken_fake_99999";

        assertFalse(tokenStoreService.isValidToken(fakeToken), "Unregistered token must be rejected");
        assertNull(tokenStoreService.getTokenMetadata(fakeToken));
    }

    @Test
    @DisplayName("Should reject null or empty token")
    void testNullTokenRejection() {
        assertFalse(tokenStoreService.isValidToken(null));
        assertFalse(tokenStoreService.isValidToken(""));
    }
}
