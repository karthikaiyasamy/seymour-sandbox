package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.JwtKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SmartConfigurationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private JwtKeyService jwtKeyService;

    @InjectMocks
    private SmartConfigurationController smartConfigurationController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(smartConfigurationController).build();
    }

    @Test
    @DisplayName("GET /.well-known/smart-configuration - Should return SMART v2.0 discovery metadata")
    void testGetSmartConfiguration() throws Exception {
        mockMvc.perform(get("/.well-known/smart-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer", is("http://localhost:8090")))
                .andExpect(jsonPath("$.authorization_endpoint", is("http://localhost:8090/oauth/authorize")))
                .andExpect(jsonPath("$.token_endpoint", is("http://localhost:8090/oauth/token")))
                .andExpect(jsonPath("$.jwks_uri", is("http://localhost:8090/.well-known/jwks.json")))
                .andExpect(jsonPath("$.grant_types_supported[0]", is("authorization_code")));
    }

    @Test
    @DisplayName("GET /.well-known/jwks.json - Should return public JWKS key set")
    void testGetJwks() throws Exception {
        when(jwtKeyService.getJwks()).thenReturn(Map.of("keys", java.util.List.of()));

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys", hasSize(0)));
    }
}
