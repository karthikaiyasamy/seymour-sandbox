package com.terryfox.hospital.controller;

import com.terryfox.hospital.service.RegionalSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/terryfox/sync")
public class RegionalSyncController {

    @Autowired
    private RegionalSyncService regionalSyncService;

    /**
     * POST /api/terryfox/sync/regional
     * Triggers multi-hospital sync dispatching synthetic patient records from Terry Fox Memorial
     * to Seymour Central EHR (Port 8090) and Langley General Gateway (C# Port 8083).
     */
    @PostMapping("/regional")
    public ResponseEntity<Map<String, Object>> triggerRegionalSync() {
        Map<String, Object> result = regionalSyncService.synchronizeRegionalNodes();
        return ResponseEntity.ok(result);
    }
}
