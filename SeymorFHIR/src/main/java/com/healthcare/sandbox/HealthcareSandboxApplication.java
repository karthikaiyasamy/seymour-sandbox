package com.healthcare.sandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class HealthcareSandboxApplication {

    public static void main(String[] args) {
        System.out.println("""
                ╔══════════════════════════════════════════════════╗
                ║   Healthcare Sandbox — Seymour Regional EHR      ║
                ║   FHIR R4 API on http://localhost:8090           ║
                ╠══════════════════════════════════════════════════╣
                ║  GET /api/fhir/Patient                           ║
                ║  GET /api/fhir/Patient/{id}                      ║
                ║  GET /api/fhir/Patient?name=chen                 ║
                ║  GET /api/fhir/Patient?mrn=MRN-10001             ║
                ║  GET /api/fhir/Encounter/adt/patient/{id}        ║
                ║  GET /api/fhir/Encounter/adt/visit/{visitNo}     ║
                ║  GET /api/fhir/MedicationRequest/patient/{id}    ║
                ║  GET /api/fhir/MedicationRequest/patient/{id}/active ║
                ║  GET /api/fhir/DocumentReference/patient/{id}    ║
                ╚══════════════════════════════════════════════════╝
                """);
        SpringApplication.run(HealthcareSandboxApplication.class, args);
    }
}
