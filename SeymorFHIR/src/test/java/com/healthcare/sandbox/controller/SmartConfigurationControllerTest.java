package com.healthcare.sandbox.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmartConfigurationControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SmartConfigurationController()).build();
    }

    @Test
    @DisplayName("GET /.well-known/smart-configuration - Should return standard SMART-on-FHIR R4 discovery metadata")
    void testGetSmartConfigurationMetadata() throws Exception {
        mockMvc.perform(get("/.well-known/smart-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorization_endpoint", is("http://localhost:8090/oauth/authorize")))
                .andExpect(jsonPath("$.token_endpoint", is("http://localhost:8090/oauth/token")))
                .andExpect(jsonPath("$.scopes_supported", hasItem("launch/patient")))
                .andExpect(jsonPath("$.scopes_supported", hasItem("patient/*.read")))
                .andExpect(jsonPath("$.grant_types_supported", hasItem("authorization_code")))
                .andExpect(jsonPath("$.capabilities", hasItem("launch-standalone")));
    }
}
