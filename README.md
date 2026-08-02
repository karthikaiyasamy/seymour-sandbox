# Healthcare Sandbox — Regional Interoperability & FHIR Workspace

A local FHIR R4-shaped REST API sandbox built with **Java (Spring Boot / HAPI FHIR R4)** and **C# (.NET 10)**, designed to simulate real-world British Columbia (BC) clinical workflows and regional healthcare integration architectures.

> ⚠️ **IMPORTANT EDUCATIONAL DISCLAIMER**:  
> **This repository is an open-source developer learning sandbox designed exclusively for learning healthcare software engineering, HL7 v2 messaging, HAPI FHIR R4 standards, and BC identity governance (Modulus-11 PHN validation). All patient names, Personal Health Numbers (PHNs), Medical Record Numbers (MRNs), diagnoses, lab results, and clinical trial data are 100% synthetic, fictitious, and artificially generated. No real Personal Health Information (PHI) is used or stored.**

> 📘 **Looking for deep integration engineering documentation?**  
> Read the complete guide: **[FHIR & Healthcare Integration Engineering Masterclass](https://github.com/karthikaiyasamy/seymour-sandbox/blob/main/FHIR_AND_HEALTHCARE_INTEGRATION_GUIDE.md)** covering HL7 v2 vs FHIR R4, Canadian Baseline, BC PHN checksum algorithms, SMART-on-FHIR, and dual-stack Java/C# patterns.

---

## What This Demonstrates

This workspace showcases a fully functional regional health interoperability sandbox, demonstrating both Java and C# enterprise backend development in a multi-stack healthcare system.

### Key Integration Capabilities
* **Hybrid Technology Stack:** Dual-stack integration featuring a Java Spring Boot clinical backend, a C# .NET 10 Web API registration gateway, a React/Vite front-end, Mirth Connect integration middleware, and PostgreSQL databases.
* **FHIR R4 Resource Suite:** Native support for `Patient`, `Encounter`, `Observation` (LOINC-coded vitals/labs), `AllergyIntolerance` (SNOMED-coded allergies), `MedicationRequest`, and `DocumentReference`.
* **Atomic FHIR Bundle Transactions:** Complete FHIR `transaction` and `batch` processing engine (`POST /api/fhir` in Java, `POST /fhir` in C#) executing atomic database operations and returning standard `transaction-response` bundles.
* **HL7 v2 Message Ingestion & Parsing (Java & C#):** Automated transformation, serialization, and ingestion of **ADT** (Admit, Discharge, Transfer), **ORU** (Observation Result / Lab Results), and **VXU** (Unsolicited Vaccination Record) messages in both Java (`Hl7Service.java`) and C# (`Hl7Parser.cs` & `Hl7IngestController.cs`).
* **BC-Standard Identity Validation (Modulus-11):** Implementation of the official British Columbia Personal Health Number (PHN) check digit validation algorithm in both Java and C# utility layers, verifying checksums and rejecting invalid cards.
* **PII Security & Masking:** Custom utility layers that mask Patient Health Information (PHI) in terminal and file logging (e.g. `9234567897` $\rightarrow$ `923****897`) to comply with BC FOIPPA data privacy regulations.
* **SMART on FHIR OAuth2 Simulation:** Secure app-launch authentication simulating authorization code grant flows, returning access tokens accompanied by launching patient context (`patient: "1"`).
* **Canadian Baseline FHIR Compliance:** Native support for CA Baseline patient profiles using BC PHNs as identifiers and BC-specific system URIs (`http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn`).

---

## Prerequisites
- **Java 21+** & **Maven 3.8+**
- **.NET 10.0 SDK**
- **PostgreSQL** (running locally on port 5432)

---

## Quick Start

### 1. Build and Run Java Backends
```bash
# Build all Java modules
mvn clean package -DskipTests

# Run Seymour FHIR Server (Java - Port 8090)
cd SeymorFHIR && mvn spring-boot:run

# Run Terry Fox Memorial Hospital HAPI FHIR Server (Java - Port 8085)
cd TerryFoxMemorial && mvn spring-boot:run

# Run Langley Children's Hospital Backend (Java - Port 8081)
cd LangleyChildrensHospital/langley-backend && mvn spring-boot:run
```

### 2. Build and Run C# Gateway
```bash
# Run Langley General Gateway (C# - Port 8083)
cd LangleyGeneralGateway
dotnet run
```

---

## API Summary Across Modules

| Module | Stack | Port | Endpoint Focus |
| :--- | :--- | :--- | :--- |
| **SeymorFHIR** | Java / Spring Boot | `8090` | FHIR R4 APIs (`/api/fhir/Patient`, `Observation`, `AllergyIntolerance`, `Encounter`, `MedicationRequest`), FHIR Bundle Transactions (`POST /api/fhir`), SMART on FHIR OAuth2 (`/oauth2/authorize`, `/oauth2/token`). |
| **TerryFoxMemorial** | Java / HAPI FHIR R4 | `8085` | Native HAPI FHIR R4 Engine (`/fhir/Patient`, `Condition`, `ResearchStudy`, `ResearchSubject`, `DiagnosticReport`), mCODE Oncology TNM Staging, Pathology/Genomic feeds (`POST /api/terryfox/hl7`), Capability Statement (`/fhir/metadata`). |
| **LangleyGeneralGateway** | C# / .NET 10 | `8083` | C# FHIR APIs (`/fhir/Patient`, `/fhir/Observation`), C# FHIR Bundle Transactions (`POST /fhir`), Native HL7 v2 Ingest (`POST /api/langleygeneral/hl7`), Patient Sync (`POST /api/langleygeneral/sync`). |
| **langley-backend** | Java / Spring Boot | `8081` | Pediatric Sync Webhooks (`/api/langley/pediatric/sync`, `/api/langley/pediatric/allergy-sync`), Patient Roster & Labs (`/api/patients`). |

---

## Project Structure

```
seymour-sandbox/
├── pom.xml                                  ← Parent Maven POM
├── README.md                                ← Main Repository documentation
├── FHIR_AND_HEALTHCARE_INTEGRATION_GUIDE.md ← Masterclass Integration Engineering Guide
│
├── SeymorFHIR/                              ← Seymour EHR FHIR Server (Java / Spring Boot)
│   ├── pom.xml
│   ├── src/main/java/com/healthcare/sandbox/
│   │   ├── config/DataSeeder.java           ← Seeding complex patient records & labs/allergies
│   │   ├── controller/
│   │   │   ├── PatientController.java       ← FHIR Patient & $match CRS endpoints
│   │   │   ├── ObservationController.java   ← FHIR LOINC Observation endpoint
│   │   │   ├── AllergyIntoleranceController.java ← FHIR SNOMED Allergy endpoint
│   │   │   └── BundleController.java        ← FHIR Transaction Bundle processor
│   │   └── util/PhnValidator.java           ← BC PHN Modulus-11 check digit logic
│
├── TerryFoxMemorial/                        ← Terry Fox Memorial Cancer Hospital (Java / HAPI FHIR R4)
│   ├── pom.xml
│   ├── src/main/java/com/terryfox/hospital/
│   │   ├── config/TerryFoxHapiServerConfig.java ← HAPI RestfulServer Servlet bean configuration
│   │   ├── provider/                        ← Native HAPI ResourceProviders (IResourceProvider)
│   │   │   ├── PatientResourceProvider.java      ← HAPI FHIR Patient & $match provider
│   │   │   ├── ConditionResourceProvider.java    ← mCODE Cancer TNM Staging provider
│   │   │   ├── ResearchStudyResourceProvider.java← Clinical Trial Protocol provider
│   │   │   ├── ResearchSubjectResourceProvider.java← Patient Trial Enrollment provider
│   │   │   └── DiagnosticReportResourceProvider.java ← NGS Genomic & Pathology provider
│   │   ├── interceptor/TerryFoxAuditInterceptor.java ← HAPI Audit logging interceptor
│   │   └── controller/Hl7OncologyIngestController.java ← HL7 v2 pathology ORU^R01 ingestion
│
├── LangleyChildrensHospital/
│   ├── langley-backend/                     ← Pediatric Portal Backend (Java / Spring Boot)
│   │   └── src/main/java/com/langley/hospital/
│   │       ├── controller/WebhookController.java ← Ingests Mirth JSON & allergy payloads
│   │       └── util/PhnValidator.java       ← Inbound validation & masked logging
│   └── langley-frontend/                    ← Langley Clinician Dashboard (Vite / React)
│
└── LangleyGeneralGateway/                   ← Hospital Gateway (C# / .NET 10 Web API)
    ├── LangleyGeneralGateway.csproj         ← EF Core & Npgsql PostgreSQL configuration
    ├── Utils/
    │   ├── PhnValidator.cs                  ← C# Modulus-11 validation & PII masking
    │   └── Hl7Parser.cs                     ← C# HL7 v2 segment parser (MSH, PID, PV1, OBX)
    └── Controllers/
        ├── FhirPatientController.cs         ← C# FHIR Patient controller
        ├── FhirObservationController.cs     ← C# FHIR Observation controller
        ├── FhirBundleController.cs           Mikro C# FHIR Bundle Transaction processor
        ├── Hl7IngestController.cs           ← C# Raw HL7 v2 ingestion controller
        └── SyncController.cs                ← Safe DTO binding & manual validation upserts
```
