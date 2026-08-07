# Healthcare Sandbox — Regional Interoperability & FHIR Workspace

A healthcare interoperability sandbox demonstrating a regional HL7 v2-to-FHIR integration workflow across **Java (Spring Boot / HAPI FHIR R4)** and **C# (.NET 10)** services, with synthetic clinical data, security and resilience patterns, and explicit production hardening considerations for British Columbia (BC) health authority architectures.

> ⚠️ **IMPORTANT EDUCATIONAL DISCLAIMER**:  
> **This repository is an open-source developer learning sandbox designed exclusively for learning healthcare software engineering, HL7 v2 messaging, HAPI FHIR R4 standards, and BC identity governance (Modulus-11 PHN validation). All patient names, Personal Health Numbers (PHNs), Medical Record Numbers (MRNs), diagnoses, lab results, and clinical trial data are 100% synthetic, fictitious, and artificially generated. No real Personal Health Information (PHI) is used or stored.**

> 📘 **Looking for deep integration engineering documentation?**  
> Read the complete guide: **[FHIR & Healthcare Integration Engineering Masterclass](https://github.com/karthikaiyasamy/seymour-sandbox/blob/main/FHIR_AND_HEALTHCARE_INTEGRATION_GUIDE.md)** covering HL7 v2 vs FHIR R4, Canadian Baseline, BC PHN checksum algorithms, SMART-on-FHIR, and dual-stack Java/C# patterns.

---

## What This Demonstrates

This workspace showcases a regional health interoperability sandbox, demonstrating both Java and C# enterprise backend development in a multi-stack healthcare system.

### Key Integration Capabilities
* **Hybrid Technology Stack:** Dual-stack integration featuring a Java Spring Boot clinical backend (`Seymour Regional EHR`), a C# .NET 10 Web API registration gateway (`Langley General`), a specialized HAPI FHIR R4 oncology server (`Terry Fox Cancer Hospital`), and PostgreSQL databases.
* **FHIR R4 Resource Suite & HAPI Providers:** Support for `Patient`, `Encounter`, `Observation` (LOINC-coded vitals/labs), `AllergyIntolerance` (SNOMED-coded allergies), `MedicationRequest`, `ResearchStudy`, and `ResearchSubject`.
* **Reliable HL7 Message Audit & MSH-10 Idempotency:** Full message lifecycle tracking (`RECEIVED` $\rightarrow$ `VALIDATED` $\rightarrow$ `TRANSFORMED` $\rightarrow$ `DELIVERED` / `FAILED`). Enforces duplicate payload rejection via **SHA-256 payload hashing** and `MSH-10` Message Control ID tracking.
* **Patient Identity Conflict Resolution:** Multi-field demographic match scoring engine (0.0 to 1.0) using MRN, PHN, name, and DOB. Automatically halts patient creation on ambiguous match scores (0.35 - 0.85) and queues records in a dedicated `PENDING_REVIEW` human conflict resolution queue (`PatientMatchReviewController`).
* **Atomic FHIR Bundle Processing:** FHIR `transaction` and `batch` processing engine (`POST /api/fhir` in Java, `POST /fhir` in C#) enforcing Spring `TransactionAspectSupport.setRollbackOnly()` database rollback on entry failures.
* **BC-Standard Identity Validation (Modulus-11):** Implementation of the official British Columbia Personal Health Number (PHN) check digit validation algorithm in both Java and C# utility layers, verifying checksums and rejecting invalid cards.
* **FOIPPA Data Privacy & PII Sanitization:** Custom log sanitization layers stripping raw patient JSON maps, MRNs, and patient names, logging correlation tracing IDs and masked PHNs (`900****071`) only.
* **SMART on FHIR OAuth2 App-Launch Auth:** OAuth2 authorization code grant flow simulation (`GET /oauth/authorize` & `POST /oauth/token`) issuing opaque Bearer tokens with active launch patient context (`patient: "1"`).
* **Resilience & Automated CI/CD Pipeline:** Semaphore Bulkhead concurrency protection (`HeavyReportService.java`) protecting core endpoints, plus a cloud **GitHub Actions CI/CD pipeline** (`.github/workflows/ci.yml`) pinning Java 21 & .NET 10 with 30 passing unit tests.
* **5-Minute Technical Interview Showcase:** Step-by-step interview script ([`docs/demo-script.md`](file:///Users/karthik/dev/seymour/seymour-sandbox/docs/demo-script.md)) formatted for live technical interviews at PHSA and Fraser Health.

---

## Prerequisites
- **Java 21+** & **Maven 3.8+**
- **.NET 10.0 SDK**
- **PostgreSQL** (running locally on port 5432)

---

## Quick Start

### 🐳 Option A: One-Command Launch via Docker Compose (Recommended)
Spin up the entire regional health infrastructure (PostgreSQL database, Seymour FHIR Server, Terry Fox Cancer Center, and Langley General C# Gateway) using Docker Compose:

```bash
docker-compose up --build
```
> **🌐 Centralized API Developer Portal:** Once running, navigate your browser to **`http://localhost:8090/swagger-ui.html`** to explore and test all APIs across the regional health network.

---

### 💻 Option B: Manual Local Terminal Launch

#### 1. Build and Run Java Backends
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

#### 2. Build and Run C# Gateway
```bash
# Run Langley General Gateway (C# - Port 8083)
cd LangleyGeneralGateway
dotnet run
```

---

## 🔒 Security & Database Seeding Guidelines

To maintain privacy and follow secure open-source practices:
* **Synthetic Data Governance:** Source control contains **version-controlled 100% synthetic seed data** and barebone SQL DDL schema files ([`V1__create_oauth_tables.sql`](file:///Users/karthik/dev/seymour/seymour-sandbox/SeymorFHIR/src/main/resources/db/migration/V1__create_oauth_tables.sql)). No real Personal Health Information (PHI) or production credentials/secrets are ever used or committed.
* **Custom Dataset Seeding Instructions:** To populate custom synthetic clinical test records or OAuth test credentials locally:
  ```sql
  -- Seed pre-authorized test OAuth code locally in PostgreSQL:
  INSERT INTO oauth_authorization_codes (code, client_id, patient_id, redirect_uri, expires_at)
  VALUES ('SMART_TEST_CODE_901', 'seymour_smart_app', '1', 'http://localhost:3000/callback', NOW() + INTERVAL '1 hour');
  ```

## API Summary Across Modules

| Module | Stack | Port | Endpoint Focus |
| :--- | :--- | :--- | :--- |
| **SeymorFHIR** | Java / Spring Boot | `8090` | FHIR R4 APIs (`/api/fhir/Patient`, `Observation`, `AllergyIntolerance`, `Encounter`, `MedicationRequest`), FHIR Bundle Transactions (`POST /api/fhir`), SMART on FHIR OAuth2 (`/oauth/authorize`, `/oauth/token`). |
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
