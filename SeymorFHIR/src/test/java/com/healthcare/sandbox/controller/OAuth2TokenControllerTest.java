package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.TokenStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OAuth2TokenControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TokenStoreService tokenStoreService;

    @InjectMocks
    private OAuth2TokenController oauth2TokenController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(oauth2TokenController).build();
    }

    @Test
    @DisplayName("GET /oauth/authorize - Should issue SMART authorization code and patient launch context")
    void testAuthorizeEndpointSuccess() throws Exception {
        mockMvc.perform(get("/oauth/authorize")
                        .param("client_id", "smart_app_test")
                        .param("launch", "1004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("AUTHORIZED")))
                .andExpect(jsonPath("$.code", startsWith("SMART_AUTH_CODE_")))
                .andExpect(jsonPath("$.patient_context", is("1004")));
    }

    @Test
    @DisplayName("POST /oauth/token - Should exchange valid authorization code for Bearer access token")
    void testTokenEndpointWithValidCode() throws Exception {
        // First issue a code via authorize
        String responseJson = mockMvc.perform(get("/oauth/authorize")
                        .param("client_id", "smart_app_test")
                        .param("launch", "1"))
                .andReturn().getResponse().getContentAsString();

        // Extract generated authorization code
        String code = responseJson.split("\"code\":\"")[1].split("\"")[0];

        // Now exchange code at /oauth/token
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("client_id", "smart_app_test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", startsWith("eySmartFhirToken_")))
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.patient", is("1")));

        verify(tokenStoreService).registerToken(org.mockito.ArgumentMatchers.startsWith("eySmartFhirToken_"), eq("1"), eq("smart_app_test"), anyInt());
    }

    @Test
    @DisplayName("POST /oauth/token - Should reject missing/invalid authorization code with 400 invalid_grant")
    void testTokenEndpointRejectsInvalidCode() throws Exception {
        mockMvc.perform(post("/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("grant_type", "authorization_code")
                        .param("code", "INVALID_FAKE_CODE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("invalid_grant")))
                .andExpect(jsonPath("$.error_description", containsString("Invalid, expired, or missing authorization code")));
    }
}
