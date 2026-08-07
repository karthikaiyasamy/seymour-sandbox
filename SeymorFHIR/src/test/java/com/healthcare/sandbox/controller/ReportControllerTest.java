package com.healthcare.sandbox.controller;

import com.healthcare.sandbox.service.HeavyReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private HeavyReportService heavyReportService;

    @InjectMocks
    private ReportController reportController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(reportController).build();
    }

    @Test
    @DisplayName("GET /api/reports/heavy-export/{patientId} - Should return 200 OK when Bulkhead capacity is available")
    void testExportHeavyReportSuccess() throws Exception {
        when(heavyReportService.generateHeavyPatientReport("1"))
                .thenReturn(Map.of("status", "SUCCESS", "patientId", "1", "reportType", "PDF_LAB_EXPORT"));

        mockMvc.perform(get("/api/reports/heavy-export/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.reportType", is("PDF_LAB_EXPORT")));
    }

    @Test
    @DisplayName("GET /api/reports/heavy-export/{patientId} - Should return 429 Too Many Requests on Bulkhead capacity rejection")
    void testExportHeavyReportBulkheadFallback429() throws Exception {
        when(heavyReportService.generateHeavyPatientReport("1"))
                .thenReturn(Map.of(
                        "httpStatus", 429,
                        "resourceType", "OperationOutcome",
                        "status", "DEGRADED_CAPACITY",
                        "message", "Bulkhead limit reached. Request rejected to preserve system stability."
                ));

        mockMvc.perform(get("/api/reports/heavy-export/1"))
                .andExpect(status().is(429))
                .andExpect(jsonPath("$.status", is("DEGRADED_CAPACITY")))
                .andExpect(jsonPath("$.resourceType", is("OperationOutcome")));
    }
}
