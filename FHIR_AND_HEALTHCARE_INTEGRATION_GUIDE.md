# FHIR & Healthcare Integration Engineering Masterclass (Java & C#)

Welcome to the **Healthcare Sandbox Integration & Interoperability Masterclass Guide**. This document provides a comprehensive, deep-dive architectural reference for integration engineers, health system software developers, and interface analysts working with **HL7 v2**, **FHIR R4**, **mCODE Oncology Data Models**, **HAPI FHIR Java Framework**, **C# .NET 10 Web APIs**, **SMART on FHIR OAuth2**, **mTLS & System-to-System Token Security**, **Mirth Connect Middleware**, **BC Longitudinal Record Access (LRA)**, and enterprise Canadian/BC EHR standards (**MEDITECH Expanse**, **Cerner Millennium**, and **Epic**).

---

## Table of Contents

1. [Architectural Overview & Ecosystem Topology](#1-architectural-overview--ecosystem-topology)
2. [HL7 v2 vs FHIR R4: Core Concepts & Protocols](#2-hl7-v2-vs-fhir-r4-core-concepts--protocols)
3. [Canadian Baseline (CA Baseline) & BC Personal Health Number (PHN) Standards](#3-canadian-baseline-ca-baseline--bc-personal-health-number-phn-standards)
4. [BC Longitudinal Record Access (LRA) & MSP Coverage Eligibility (HIBC)](#4-bc-longitudinal-record-access-lra--msp-coverage-eligibility-hibc)
5. [Enterprise System-to-System Security: mTLS & OAuth2 Token Exchange](#5-enterprise-system-to-system-security-mtls--oauth2-token-exchange)
6. [Enterprise EHR Architectures: MEDITECH, Cerner Millennium, & Epic](#6-enterprise-ehr-architectures-meditech-cerner-millennium--epic)
7. [Seymour Regional EHR (Java / Spring Boot - Port 8090)](#7-seymour-regional-ehr-java--spring-boot---port-8090)
8. [Terry Fox Memorial Hospital: HAPI FHIR R4 Engine & Oncology (Java - Port 8085)](#8-terry-fox-memorial-hospital-hapi-fhir-r4-engine--oncology-java---port-8085)
9. [Langley General Gateway (C# .NET 10 - Port 8083)](#9-langley-general-gateway-c-net-10---port-8083)
10. [Langley Children's Hospital Backend (Java - Port 8081)](#10-langley-childrens-hospital-backend-java---port-8081)
11. [Multi-Hospital Regional Interoperability Sync Pipeline](#11-multi-hospital-regional-interoperability-sync-pipeline)
12. [Integration Engine Pipeline (Mirth Connect & Webhooks)](#12-integration-engine-pipeline-mirth-connect--webhooks)
13. [SMART on FHIR OAuth2 Authentication & Launch Context](#13-smart-on-fhir-oauth2-authentication--launch-context)
14. [End-to-End API Testing & Curl Command Reference](#14-end-to-end-api-testing--curl-command-reference)
15. [Reliable HL7 Message Audit, MSH-10 Idempotency & Tracing](#15-reliable-hl7-message-audit-msh-10-idempotency--tracing)
16. [Patient Identity Conflict Resolution & PENDING_REVIEW Queue](#16-patient-identity-conflict-resolution--pending_review-queue)
17. [Persistent RSA Key Stores & Zero-Downtime Key Rotation](#17-persistent-rsa-key-stores--zero-downtime-key-rotation-kid-cache-eviction)
18. [Cross-Hospital EMPI Identity Reconciliation Engine](#18-cross-hospital-empi-identity-reconciliation-engine)

---

## Synthetic Data & Open Source Developer Disclaimer
> **This repository and guide represent an open-source educational developer sandbox designed exclusively for learning healthcare software engineering, HL7 v2 messaging, HAPI FHIR R4 standards, and BC identity governance (Modulus-11 PHN validation). All patient names, Personal Health Numbers (PHNs), Medical Record Numbers (MRNs), diagnoses, lab results, and clinical trial data are 100% synthetic, fictitious, and artificially generated. No real Personal Health Information (PHI) is used or stored.**

---

## 1. Architectural Overview & Ecosystem Topology

The sandbox simulates a regional health authority ecosystem consisting of four distinct microservices and an integration engine middleware:

### Sub-Module Documentation Links:
* **[Seymour Regional EHR Module README](SeymorFHIR/README.md)**
* **[Terry Fox Cancer Hospital Module README](TerryFoxMemorial/README.md)**
* **[Angular SMART Clinical Portal Module README](smart-app/README.md)**
* **[Langley General Gateway C# Module README](LangleyGeneralGateway/README.md)**
* **[Langley Children's Hospital Workspace README](LangleyChildrensHospital/README.md)**
* **[System Architecture Overview Guide](docs/ARCHITECTURE_OVERVIEW.md)**

```
                               +-------------------------------------------------------+
                               |                Seymour Regional EHR                   |
                               |          (Java / Spring Boot / Custom REST)           |
                               |    Port: 8090 | DB: seymour_db (PostgreSQL / H2)        |
                               +---------------------------+---------------------------+
                                                           |
                                                           | Multi-Hospital FHIR Regional Sync
                                                           v
+----------------------------------------------------------+----------------------------------------------------------+
|                        Terry Fox Memorial Cancer Hospital                                                           |
|                     (Java 21 / Spring Boot 3.2.5 / HAPI FHIR R4 Engine)                                            |
|                               Port: 8085 | DB: terryfox_db (H2 / PostgreSQL)                                           |
|                                                                                                                     |
|  - Native HAPI RestfulServer Servlet (/fhir/*)          - mCODE Oncology TNM Staging (Condition)                    |
|  - HAPI Resource Providers (Patient, Condition, etc.)   - Clinical Trial Protocol Matching (ResearchStudy/Subject) |
|  - HAPI Interceptor PII Audit Logging                   - NGS Genomic & Pathology Ingestion (ORU^R01 / MDM^T02)    |
+------------------------------+----------------------------------------------------------+---------------------------+
                               |                                                          |
             HTTP Webhook Sync |                                                          | Multi-Hospital Sync
                               v                                                          v
  +----------------------------+-----------------------+      +---------------------------+---------------------------+
  |    Langley Children's Hospital Backend             |      |             Langley General Gateway                       |
  |           (Java / Spring Boot)                     |      |              (C# / .NET 10 Web API)                       |
  |         Port: 8081 | DB: langley_db          |      |         Port: 8083 | DB: langley_general_db           |
  +----------------------------------------------------+      +-------------------------------------------------------+
                               ^                                                          ^
                               |                                                          |
                               +------------------------- Mirth Connect ------------------+
                                                 (TCP MLLP Port 9085 -> Webhook)
```

### Microservice Roles & Architectural Highlights:

1. **Seymour FHIR Server (`SeymorFHIR` — Java / Port 8090):** [Detailed Code Walkthrough README](SeymorFHIR/README.md)
   * Primary Regional EHR Repository.
   * Exposes RESTful FHIR R4 resources (`Patient`, `Encounter`, `Observation`, `AllergyIntolerance`, `MedicationRequest`, `DocumentReference`).
   * Implements atomic FHIR `transaction` bundles, SMART-on-FHIR OAuth2 server (`/oauth2/authorize`, `/oauth2/token`), persistent PostgreSQL RSA key storage (`oauth_keys`), live key rotation (`/api/admin/rotate-keys`), and Patient Master Index (`$match`).
2. **Terry Fox Memorial Cancer Hospital (`TerryFoxMemorial` — Java / Port 8085):** [Detailed Code Walkthrough README](TerryFoxMemorial/README.md)
   * Specialized Oncology & Clinical Trials Node powered by the official **HAPI FHIR R4 Engine (`ca.uhn.fhir.rest.server.RestfulServer`)**.
   * Implements mCODE (Minimal Common Oncology Data Elements) TNM staging, clinical trial protocols (`ResearchStudy` / `ResearchSubject`), NGS genomic reports (`DiagnosticReport`), HL7 v2 pathology ingestion (`ORU^R01`/`MDM^T02`), dynamic `kid` cache eviction, and HAPI security interceptor.
3. **Langley General Gateway (`LangleyGeneralGateway` — C# .NET 10 / Port 8083):** [Detailed Code Walkthrough README](LangleyGeneralGateway/README.md)
   * Enterprise C# registration gateway and FHIR API service.
   * Handles pipe-delimited HL7 v2 ingestion (`MSH`, `PID`, `PV1`, `OBX`), C# FHIR R4 bundle processing, timezone-safe `DateOnly` birthdate binding, and C# Modulus-11 PHN validation.
4. **Langley Children's Hospital Backend (`langley-backend` — Java / Port 8081):** [Detailed Code Walkthrough README](LangleyChildrensHospital/README.md)
   * Pediatric portal backend consuming webhook synchronization payloads (immunizations, pediatric lab panels, allergy updates) emitted downstream by integration middleware.

---

## 2. HL7 v2 vs FHIR R4: Core Concepts & Protocols

Health integration engineers bridge legacy **HL7 v2** pipe-delimited messages and modern **FHIR R4** JSON RESTful APIs daily.

### 2.1 Comparison Matrix

| Dimension | HL7 v2 (Legacy / Enterprise EHR) | FHIR R4 (Modern Interoperability) |
| :--- | :--- | :--- |
| **Data Format** | Pipe-delimited text (`MSH|^~\&|...`) | JSON / XML / Turtle (`"resourceType": "Patient"`) |
| **Transport Layer** | TCP/IP sockets with MLLP framing (`0x0B ... 0x1C 0x0D`) | HTTP/S REST, Webhooks, WebSockets |
| **Data Structure** | Segments (`PID`, `PV1`, `OBX`) & Fields | Domain Resources (`Patient`, `Observation`, `Encounter`, `Condition`) |
| **Trigger Mechanism** | Event-driven (e.g. `ADT^A01` on patient admission) | RESTful CRUD (`GET`, `POST`, `PUT`, `DELETE`), Operations (`$match`), Subscriptions |
| **Terminology** | HL7 Tables, local hospital code sets | LOINC, SNOMED CT, RxNorm, ICD-10, UCUM |
| **Validation** | Segment length & delimiter checks | Structural FHIR schemas, `OperationOutcome`, HAPI `FhirValidator` |

### 2.2 HL7 v2 Segment to FHIR R4 Resource Mapping

| HL7 v2 Segment | Standard Meaning | Corresponding FHIR R4 Resource | Key Fields / Purpose |
| :--- | :--- | :--- | :--- |
| **`PID`** | Patient Identification | **`Patient`** | Demographics (MRN, PHN, Name, DOB, Sex, Address) |
| **`PV1`** | Patient Visit | **`Encounter`** | Class (Inpatient/Outpatient), Ward, Room, Bed, Attending Doctor |
| **`DG1`** | Diagnosis | **`Condition`** (or `Encounter.diagnosis`) | Diagnosis Description, Code (ICD-10/SNOMED CT), TNM Staging |
| **`OBX`** | Observation / Result / Vitals | **`Observation`** | Lab Test Values (HbA1c, Glucose, NGS Biomarkers), Vitals, Units |
| **`AL1`** | Allergy Information | **`AllergyIntolerance`** | Allergen (Penicillin, Peanuts), Category, Severity, Reaction |
| **`RXA` / `RXO`** | Pharmacy Admin / Order | **`MedicationRequest` / `MedicationAdministration`** | Medication Name, RxNorm Code, Dosage, Frequency, Route |
| **`OBR`** | Observation Request | **`DiagnosticReport`** | Order metadata, specimen source, conclusion narrative |

---

## 3. Canadian Baseline (CA Baseline) & BC Personal Health Number (PHN) Standards

In Canadian health informatics (PHSA, Fraser Health, Provincial Client Registries), interoperability specifications mandate strict conformance to Canadian extensions:

### 3.1 BC Personal Health Number (PHN) System URIs
BC PHNs are registered under standard naming system URIs:
* **Infoway Canada Standard URI**: `https://fhir.infoway-inforoute.ca/NamingSystem/ca-bc-patient-healthcare-id`
* **Shared Health Exchange URI**: `http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn`

### 3.2 BC PHN Modulus-11 Validation Algorithm
A valid BC PHN is **10 digits**, starts with a **9**, and passes the Modulus-11 check digit calculation across digits 2 to 9:

$$\text{Sum} = \sum_{i=2}^{9} d_i \times w_{i-1}$$

Where weights $w = [2, 4, 8, 5, 10, 9, 7, 3]$.
The remainder is $R = \text{Sum} \pmod{11}$. If $R = 0$ or $R = 1$, the PHN is invalid.
The calculated check digit is $11 - R$. The 10th digit of the PHN must equal this check digit.

### 3.3 FOIPPA PII Security & Sanitization
To comply with British Columbia's Freedom of Information and Protection of Privacy Act (FOIPPA), PII in terminal logs and audit traces is sanitized (`9234567897` $\rightarrow$ `923****897`).

---

## 4. BC Longitudinal Record Access (LRA) & MSP Coverage Eligibility (HIBC)

The **BC Longitudinal Record Access (LRA)** FHIR Implementation Guide specifies provincial standards for validating patient insurance coverage directly with **Health Insurance BC (HIBC)**.

### 4.1 Verification Query Mechanics
To query HIBC for BC Medical Services Plan (MSP) active coverage, clinical systems execute a FHIR RESTful GET query against **`CoverageEligibilityResponse`**:

```http
GET /CoverageEligibilityResponse?patient.identifier=https://fhir.infoway-inforoute.ca/NamingSystem/ca-bc-patient-healthcare-id|9234567897&servicedate=2026-08-02
Accept: application/fhir+json
```

### 4.2 Architectural Key Elements
1. **Token Syntax**: `patient.identifier` is passed as `system|value`.
2. **Date of Service**: `servicedate` parameter specifies the clinical encounter date.
3. **Response Resource**: Returns a `CoverageEligibilityResponse` with `inforce: true` and `insurer: Health Insurance BC (HIBC)`.

---

## 5. Enterprise System-to-System Security: mTLS & OAuth2 Token Exchange

Connecting external applications to provincial health endpoints (HIBC, BC Client Registry CRS) or third-party EHR portals requires strict **mTLS and OAuth2 Client Credentials Security**:

```
[ Clinical Application Server ]                                 [ Provincial Trust Gateway / Token Endpoint ]
               │                                                                      │
               ├─── 1. HTTPS 2-Way mTLS Handshake (Presents Client Cert x509) ───────►│
               │    (Validates Whitelisted Certificate Common Name - CN)               │
               │                                                                      │
               ├─── 2. POST /auth Token Request (client_id + signed JWT Assertion) ──►│
               │                                                                      │
               │◄── 3. Returns OAuth2 Bearer Access Token ────────────────────────────┤
Regional health systems enforce strict system-to-system security boundaries using Mutual TLS (mTLS) for transport-layer security and OAuth2 Client Credentials grant flows for API authentication.

---

## 6. Enterprise EHR Architectures: MEDITECH, Cerner Millennium, & Epic

Understanding how the "Big 3" enterprise Health Information Systems (HIS) operate in regional health networks is essential for integration software engineers:

### 6.1 MEDITECH (Regional Primary EHR)
* **Architecture**: Enterprise EHR platform (Expanse / MAGIC) powering regional emergency, registration, laboratory, and inpatient wards.
* **Engineering Responsibilities**:
  1. **HL7 v2 Interface Management**: Configuring `ADT^A01/A04/A08` demographic feeds and `ORU^R01` lab/radiology result interfaces.
  2. **MEDITECH Data Repository (DR) & T-SQL**: Querying MEDITECH's SQL Server Data Repository (DR) tables (`AdmPatients`, `PharMedications`, `LALibrary`) using T-SQL for clinical reporting and census dashboards.
  3. **MEDITECH Expanse REST / FHIR APIs**: Connecting external applications via RESTful FHIR R4 endpoints.

### 6.2 Cerner Millennium / CST Cerner (Regional Health Networks)
* **Architecture**: The regional EHR deployed under large-scale clinical transformation projects across acute, ambulatory, and specialty cancer centers.
* **Engineering Responsibilities**:
  1. **Cerner Ignite FHIR R4 APIs (`code.cerner.com`)**: Integrating web/mobile clinical applications using Cerner's Ignite R4 FHIR APIs, enforcing SMART-on-FHIR OAuth2 security scopes (`patient/Patient.read`, `patient/Observation.read`).
  2. **Foreign System Interfaces (FSI) & Mirth/Rhapsody**: Configuring Mirth Connect or Rhapsody interface channels to route messages between Cerner Millennium and provincial services (BC Client Registry CRS).
  3. **Cerner CCL (Cerner Command Language)**: Writing SQL-like CCL queries against Cerner Millennium tables (`person`, `encounter`, `orders`, `clinical_event`).

### 6.3 Epic (SMART-on-FHIR & Interconnect Engine)
* **Architecture**: Dominant North American EHR setting industry benchmarks for SMART-on-FHIR integration.
* **Engineering Responsibilities**:
  1. **Epic Bridges**: Managing HL7 v2 interfaces for registration (`ADT`), scheduling (`SIU`), and billing (`DFT`).
  2. **SMART-on-FHIR App Launch**: Connecting third-party clinical apps via OAuth2 Authorization Code Grant flows with patient launch context (`launch/patient`).

---

## 7. Seymour Regional EHR (Java / Spring Boot - Port 8090)

### 7.1 Architecture & Tech Stack
- **Framework:** Spring Boot 3.2.5 with Java 21
- **Database:** PostgreSQL (`seymour_db`) with H2 fallback
- **Serialization:** Jackson FHIR JSON mapping + HAPI FHIR Core structures

### 7.2 Core Capabilities
1. **FHIR R4 Resource Suite (`/api/fhir/*`):** `Patient`, `Encounter`, `Observation` (LOINC lab/vitals), `AllergyIntolerance` (SNOMED-CT allergies), `MedicationRequest`, and `DocumentReference`.
2. **Atomic FHIR Bundle Transactions (`POST /api/fhir`):** Executes `@Transactional` batch/transaction bundle processing in Spring Data JPA.
3. **SMART-on-FHIR OAuth2 Server (`/oauth2/authorize`, `/oauth2/token`):** Simulates authorization code grant flows, returning access tokens with patient launch context (`patient: "1"`).

---

## 8. Terry Fox Memorial Hospital: HAPI FHIR R4 Engine & Oncology (Java - Port 8085)

### 8.1 Architecture & Native HAPI Framework
`TerryFoxMemorial` is a specialized **Oncology and Clinical Trials Hospital Node** built on the official **HAPI FHIR R4 Server Framework (`ca.uhn.fhir.rest.server.RestfulServer`)**:

* **Servlet Mounting ([TerryFoxHapiServerConfig.java](file:///Users/karthik/dev/cerner/seymour-sandbox/TerryFoxMemorial/src/main/java/com/terryfox/hospital/config/TerryFoxHapiServerConfig.java)):**
  Registers `RestfulServer` mounted at `/fhir/*` on port `8085`. Automatically generates the server's `CapabilityStatement` at `GET /fhir/metadata`.
* **Native Resource Providers (`IResourceProvider`):**
  * `PatientResourceProvider`: `@Read`, `@Search`, `@Create`, `@Operation(name="$match")`.
  * `ConditionResourceProvider`: mCODE Oncology TNM Staging extensions.
  * `ResearchStudyResourceProvider` & `ResearchSubjectResourceProvider`: Clinical Trial protocols and subject enrollments.
  * `DiagnosticReportResourceProvider`: NGS Genomic biomarker panel reports.
* **HAPI Audit Interceptor ([TerryFoxAuditInterceptor.java](file:///Users/karthik/dev/cerner/seymour-sandbox/TerryFoxMemorial/src/main/java/com/terryfox/hospital/interceptor/TerryFoxAuditInterceptor.java)):**
  Annotated with `@Interceptor` and `@Hook(Pointcut.SERVER_INCOMING_REQUEST_POST_PROCESSED)` to log FHIR operations and client IP addresses (`ServletRequestDetails`).

### 8.2 mCODE Oncology Data Modeling (Minimal Common Oncology Data Elements)
* **TNM Cancer Staging (`Condition.stage`):**
  Models AJCC 8th Edition staging (e.g. Stage IIIb NSCLC: `T3N2M0`, Stage IV Colorectal Carcinoma: `T4aN2bM1a`).
* **Genomic NGS Biomarkers (`DiagnosticReport` & `Observation`):**
  Models high-throughput biomarker mutation findings (*EGFR* Exon 19 Deletion, *KRAS* Wild Type, *HER2* 3+ Overexpression, *PD-L1* TPS 85%).
* **HL7 v2 Pathology Ingestion (`POST /api/terryfox/hl7`):**
  Parses raw `ORU^R01` / `MDM^T02` pathology messages and generates HAPI FHIR `DiagnosticReport` resources automatically.

---

## 9. Langley General Gateway (C# .NET 10 - Port 8083)

### 9.1 Architecture & Tech Stack
- **Framework:** ASP.NET Core Web API (.NET 10)
- **ORM:** Entity Framework Core
- **Database:** PostgreSQL (`langley_general_db`)

### 9.2 Core Capabilities
1. **Timezone-Safe Date Processing:** Uses C# `DateOnly` for `DateOfBirth` to eliminate UTC/Pacific timezone shifts.
2. **Native HL7 v2 Ingest Controller (`POST /api/langleygeneral/hl7`):** Utilizes `Utils/Hl7Parser.cs` to parse pipe-delimited text (`MSH`, `PID`, `PV1`, `OBX`), extract patient demographics & attached lab observations, validate BC PHN, and upsert records into PostgreSQL.
3. **C# FHIR R4 Controllers (`/fhir/Patient`, `/fhir/Observation`, `/fhir`):** Side-by-side .NET 10 implementations of FHIR REST endpoints matching the Java Seymour server interface.

---

## 10. Langley Children's Hospital Backend (Java - Port 8081)

- **Framework:** Spring Boot 3.2.5 with Java 21
- **Role:** Specialized pediatric portal backend listening for HTTP webhook synchronization payloads (`/api/langley/pediatric/sync`, `/api/langley/pediatric/allergy-sync`) dispatched by integration middleware.

---

## 11. Multi-Hospital Regional Interoperability Sync Pipeline

The **Regional Sync Pipeline** connects all active health authority nodes in the sandbox:

```
[Terry Fox Memorial Hospital - Port 8085]
                 │
                 ├────── HTTP POST (FHIR Patient JSON) ─────► [Seymour Central EHR - Port 8090]
                 │
                 └────── HTTP POST (Sync DTO Payload) ─────► [Langley General Gateway - Port 8083]
```

* **Service Class:** `com.terryfox.hospital.service.RegionalSyncService`
* **Trigger Endpoint:** `POST http://localhost:8085/api/terryfox/sync/regional`
* **Features:** Converts HAPI FHIR oncology patient entities into peer FHIR R4 and DTO payloads, enforces BC PHN checksums, and dispatches multi-node REST calls with graceful try/catch offline fallback logging.

---

## 12. Integration Engine Pipeline (Mirth Connect & Webhooks)

In enterprise hospital integration, **Mirth Connect** acts as the central message broker:

1. **TCP MLLP Ingestion:** Mirth listens on TCP port `9085`.
2. **Parsing & Mapping:** Converts raw HL7 `PID-3` (MRN), `OBX-3` (Test Code), `OBX-5` (Value) segments into JSON DTOs.
3. **HTTP Webhook Dispatch:** Posts JSON payloads to downstream subscriber endpoints:
   - `POST http://localhost:8081/api/langley/pediatric/sync`
   - `POST http://localhost:8081/api/langley/pediatric/allergy-sync`

---

## 13. SMART on FHIR — Deep Dive: OAuth2, Launch Sequences & JWT Security

> 🎯 **Goal of this section:** After reading this, you will be able to explain SMART on FHIR to a hiring manager, debug an auth failure in production code, and understand exactly what your sandbox is doing under the hood.

---

### 13.0 — Why Does SMART on FHIR Exist? (Start Here)

**The problem it solves:**

Before SMART on FHIR, every EHR vendor (Epic, Cerner, MEDITECH) had its own proprietary way to authenticate apps. A clinical decision support app built for Epic could not be plugged into Cerner without a full rewrite of authentication logic. Worse, there was no standard way for an app to know *which patient* the clinician was currently looking at in the EHR.

**The two problems SMART solves:**
1. **Authentication standardization** — SMART = OAuth2 for healthcare. Every FHIR-compliant EHR speaks the same auth language.
2. **Launch context** — SMART adds the concept of a "launch" — when a clinician opens an app from inside an EHR, the app instantly knows: *which patient? which encounter? which user?* without the clinician having to search again.

**Real-world example:**
```
Doctor is viewing patient Margaret Chen in Epic EHR.
Doctor clicks "Open Oncology Risk App" from the EHR menu.

WITHOUT SMART: App opens blank. Doctor must search for Margaret Chen again. Friction = errors.
WITH SMART:    App opens already loaded with Margaret Chen's context. Safe. Efficient.
```

**Who created it:** HL7 SMART on FHIR is maintained by the SMART Health IT project (Boston Children's Hospital + Harvard Medical School). It is the global standard for clinical app authentication on FHIR servers.

---

### 13.1 — OAuth2 Authorization Code Grant — Built From the Ground Up

> You know: "OAuth2 = login with Google." Let's go one level deeper.

**The 5 actors in every OAuth2 flow:**

| Actor | Who They Are | In Our Sandbox |
|---|---|---|
| **Resource Owner** | The human whose data it is | The clinician (or patient) |
| **Client** | The app requesting access | `smart-app` Angular portal |
| **Authorization Server** | Issues tokens after verifying identity | `SeymorFHIR` (port 8090) |
| **Resource Server** | The API holding the protected data | `TerryFoxMemorial` (port 8085) |
| **FHIR Server** | In healthcare: same as Resource Server | `TerryFoxMemorial` HAPI FHIR |

**The Authorization Code Grant — step by step:**

```
STEP 1: App redirects user to Authorization Server to log in

  smart-app ──────────────────────────────────────────────► SeymorFHIR
  GET /oauth2/authorize?
      response_type=code
      &client_id=my_clinical_app
      &redirect_uri=http://localhost:3000/callback
      &scope=launch/patient patient/*.read openid
      &state=abc123                    ← CSRF protection token
      &launch=xyz789                   ← SMART-specific: EHR gave us this

STEP 2: User logs in at Authorization Server. Server redirects back to app.

  SeymorFHIR ──────────────────────────────────────────────► smart-app
  HTTP 302 → http://localhost:3000/callback?
      code=AUTH_CODE_HERE              ← short-lived, single-use code
      &state=abc123                    ← must match what we sent in Step 1

STEP 3: App exchanges authorization code for access token (server-to-server)

  smart-app ──────────────────────────────────────────────► SeymorFHIR
  POST /oauth2/token
  Content-Type: application/x-www-form-urlencoded
  Body:
      grant_type=authorization_code
      &code=AUTH_CODE_HERE
      &redirect_uri=http://localhost:3000/callback
      &client_id=my_clinical_app

STEP 4: Authorization Server returns JWT access token

  SeymorFHIR ──────────────────────────────────────────────► smart-app
  {
    "access_token": "eyJhbGciOiJSUzI1NiJ9...",   ← JWT signed with RS256
    "token_type": "Bearer",
    "expires_in": 3600,
    "scope": "launch/patient patient/*.read openid",
    "patient": "patient-001",                      ← SMART context: WHO is being viewed
    "encounter": "enc-2026-08-01"                  ← SMART context: WHICH visit
  }

STEP 5: App uses access token to call FHIR Resource Server

  smart-app ──────────────────────────────────────────────► TerryFoxMemorial
  GET /fhir/Patient/patient-001
  Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...

STEP 6: Resource Server verifies token → returns FHIR data

  TerryFoxMemorial ────────────────────────────────────────► smart-app
  { "resourceType": "Patient", "id": "patient-001", ... }
```

**Why "authorization code" and not just sending password directly?**

The authorization code step means your app's `client_secret` (or PKCE verifier) is NEVER exposed in a browser URL or redirect. The code is short-lived (30–60 seconds). Even if someone intercepts the redirect URL, the code is useless without the client credentials to exchange it.

---

### 13.2 — SMART on FHIR = OAuth2 + 3 Healthcare Additions

Standard OAuth2 gives you authentication. SMART on FHIR adds three things that make it work in clinical environments:

#### Addition 1: Discovery Endpoint (`.well-known/smart-configuration`)

Before starting any OAuth2 flow, a SMART client MUST discover the auth server's endpoints automatically. No hardcoded URLs.

```
smart-app calls:
GET http://localhost:8090/.well-known/smart-configuration

SeymorFHIR responds:
{
  "issuer": "http://localhost:8090",
  "authorization_endpoint": "http://localhost:8090/oauth2/authorize",
  "token_endpoint": "http://localhost:8090/oauth2/token",
  "jwks_uri": "http://localhost:8090/oauth2/jwks",
  "scopes_supported": ["openid", "launch", "launch/patient", "patient/*.read"],
  "response_types_supported": ["code"],
  "capabilities": ["launch-ehr", "launch-standalone", "context-ehr-patient"]
}
```

> **Why this matters:** The app discovers everything from one endpoint. When PHSA migrates their auth server to a new URL, every SMART app automatically picks up the new configuration. No code changes needed.

#### Addition 2: The `launch` Parameter

This is the core of what makes SMART different. When an EHR opens a clinical app, it passes a `launch` token — an opaque identifier that encodes the current clinical context (patient + encounter + user) into the authorization request.

```
EHR opens app with:
http://localhost:3000?
    iss=http://localhost:8090    ← where is the FHIR server?
    &launch=opaque_launch_token  ← what is the clinical context?

App then sends this launch token in the authorize request:
GET /oauth2/authorize?
    ...&launch=opaque_launch_token

Auth server decodes the launch token → knows patient/encounter → includes in JWT
```

#### Addition 3: Healthcare-Specific Scopes

SMART defines standard scope strings that map to FHIR resource access:

| Scope | Meaning |
|---|---|
| `launch` | Required for EHR Launch — tells auth server this is an EHR-initiated launch |
| `launch/patient` | Standalone Launch — app needs patient selection (no pre-existing context) |
| `patient/*.read` | Read ANY FHIR resource for the context patient |
| `patient/Observation.read` | Read ONLY Observation resources for the patient |
| `patient/MedicationRequest.write` | Write medication orders (dangerous scope — requires strong auth) |
| `user/*.read` | Read resources across all patients the logged-in user can access |
| `offline_access` | Receive a refresh token (long-lived sessions) |
| `openid profile fhirUser` | OIDC: return user identity info in the token |

> **Key rule:** Scopes follow the pattern `[context]/[ResourceType].[permission]`
> - context = `patient` (one patient) or `user` (user's patients)
> - ResourceType = any FHIR resource name, or `*` for all
> - permission = `read`, `write`, or `*`

---

### 13.3 — Two Launch Sequences: EHR Launch vs Standalone Launch

This is the #1 interview topic for SMART on FHIR. Know both cold.

#### EHR Launch (Clinician opens app FROM INSIDE the EHR)

```
SCENARIO: Dr. Patel is viewing patient Margaret Chen in Seymour EHR.
          She clicks "Open Oncology Risk Calculator" from the EHR sidebar.

1. EHR generates a launch token encoding Margaret's context.

   SeymorFHIR EHR ──────────────────────────────────────────► smart-app
   Opens URL: http://localhost:3000?
       iss=http://localhost:8090
       &launch=LAUNCH_TOKEN_XYZ

2. App makes SMART discovery call.

   smart-app ──────────────────────────────────────────────► SeymorFHIR
   GET /.well-known/smart-configuration
   → discovers authorize/token/jwks endpoints

3. App redirects to authorize endpoint WITH the launch token.

   smart-app ──────────────────────────────────────────────► SeymorFHIR
   GET /oauth2/authorize?
       response_type=code
       &client_id=my_clinical_app
       &redirect_uri=http://localhost:3000/callback
       &scope=launch patient/*.read openid     ← "launch" scope required
       &launch=LAUNCH_TOKEN_XYZ               ← pass the launch token back
       &state=CSRF_TOKEN

4. Auth server decodes launch token → identifies Margaret's patient ID.
   Clinician is already authenticated (SSO). No second login needed.
   Auth server issues authorization code.

5. App exchanges code for token.

   POST /oauth2/token → receives:
   {
     "access_token": "eyJ...",
     "patient": "patient-margaret-chen-001",    ← context automatically included
     "encounter": "enc-20260809-001",
     "scope": "launch patient/*.read openid"
   }

6. App is now loaded in patient context. No searching. No friction.
```

**Key characteristic of EHR Launch:**
- Uses `scope=launch` (not `launch/patient`)
- The `launch` parameter carries the context
- The clinician typically does NOT see a second login screen (SSO)
- Patient context comes back in the token automatically

---

#### Standalone Launch (App opens independently — no EHR context)

```
SCENARIO: Patient Margaret Chen opens a patient-facing health portal app
          directly from her phone's app store. No EHR pre-context.

1. App opens. Needs to know which FHIR server to connect to.
   (Often hardcoded or configured in app settings)

2. App makes SMART discovery call.

   MobileApp ──────────────────────────────────────────────► SeymorFHIR
   GET /.well-known/smart-configuration

3. App redirects to authorize — requesting patient selection.

   MobileApp ──────────────────────────────────────────────► SeymorFHIR
   GET /oauth2/authorize?
       response_type=code
       &client_id=patient_portal_app
       &redirect_uri=myapp://callback
       &scope=launch/patient patient/*.read openid    ← "launch/patient" not "launch"
       &state=CSRF_TOKEN
       (NO launch parameter — there is no pre-existing context)

4. User sees login screen. Logs in with credentials.
   Auth server may show patient picker UI if user has access to multiple patients.

5. App exchanges code for token.

   POST /oauth2/token → receives:
   {
     "access_token": "eyJ...",
     "patient": "patient-margaret-chen-001",   ← patient selected by user during auth
     "scope": "launch/patient patient/*.read openid"
   }
```

**Key characteristic of Standalone Launch:**
- Uses `scope=launch/patient` (not `scope=launch`)
- NO `launch` parameter in the authorize URL
- User always sees a login screen
- Patient context comes from user selection, not EHR pre-context

---

#### Side-by-Side Comparison

| | EHR Launch | Standalone Launch |
|---|---|---|
| **Initiator** | EHR opens the app | User opens the app directly |
| **Scope used** | `launch` | `launch/patient` |
| **Launch parameter** | `&launch=TOKEN` included | No launch parameter |
| **Login screen** | Usually none (SSO) | Always shown |
| **Patient context source** | EHR passes via launch token | User selects during auth |
| **Use case** | Clinical workflow tools | Patient portals, consumer apps |
| **Example** | Oncology Risk App in hospital EHR | MyHealth BC patient portal |

---

### 13.4 — JWT Access Token: Anatomy & What Each Field Means

A JWT (JSON Web Token) has 3 parts separated by dots: `HEADER.PAYLOAD.SIGNATURE`

Each part is Base64url encoded. You can decode any JWT at [jwt.io](https://jwt.io).

#### Header
```json
{
  "alg": "RS256",        ← algorithm: RSA Signature with SHA-256
  "typ": "JWT",          ← token type
  "kid": "key-2026-08"   ← Key ID: tells verifier WHICH public key to use (for rotation)
}
```

#### Payload (Claims)
```json
{
  "iss": "http://localhost:8090",             ← Issuer: who created this token
  "sub": "user-dr-patel-001",                 ← Subject: who the token is about
  "aud": "http://localhost:8085/fhir",        ← Audience: which server this token is for
  "exp": 1723248000,                          ← Expiry: Unix timestamp (NEVER trust expired tokens)
  "iat": 1723244400,                          ← Issued At: when it was created
  "jti": "unique-token-id-abc",               ← JWT ID: unique per token (for blacklisting)
  "scope": "launch patient/*.read openid",    ← What the app is allowed to do
  "patient": "patient-margaret-chen-001",     ← SMART context: current patient
  "encounter": "enc-20260809-001",            ← SMART context: current encounter
  "fhirUser": "Practitioner/dr-patel-001"     ← OIDC: who is the logged-in user
}
```

#### Signature
```
RS256_Sign(
  base64url(header) + "." + base64url(payload),
  PRIVATE_KEY
)
```

The signature is created using the auth server's **private RSA key**. It can only be verified using the matching **public key** (exposed via JWKS). This is asymmetric cryptography — the auth server keeps the private key secret forever; anyone can have the public key.

**Why RS256 over HS256?**

| | HS256 (Symmetric) | RS256 (Asymmetric) |
|---|---|---|
| Signing key | Same secret for sign + verify | Private key signs, public key verifies |
| Key sharing | Every verifier needs the secret | Public key can be published freely |
| Security | If any verifier is compromised, all tokens are at risk | Private key never leaves the auth server |
| Healthcare fit | Not suitable for multi-service environments | ✅ Industry standard for FHIR |

---

### 13.5 — JWKS: How Token Signature Verification Works

JWKS = JSON Web Key Set. It's the auth server's public key(s) exposed as a JSON endpoint.

```
SeymorFHIR exposes:
GET http://localhost:8090/oauth2/jwks

Response:
{
  "keys": [
    {
      "kty": "RSA",                ← Key type
      "use": "sig",                ← Used for signatures (not encryption)
      "kid": "key-2026-08",        ← Key ID — must match JWT header's kid
      "alg": "RS256",
      "n": "0vx7agoebGcQ...",      ← RSA modulus (the public key material)
      "e": "AQAB"                  ← RSA exponent
    }
  ]
}
```

**What happens when TerryFoxMemorial receives a JWT:**

```
Step 1: Decode the JWT header → extract "kid": "key-2026-08"
Step 2: Look up kid in cached JWKS → find the matching public key
Step 3: Verify signature using that public key
        → if valid: token is authentic (created by SeymorFHIR, not tampered with)
        → if invalid: reject with HTTP 401 Unauthorized
Step 4: Check claims:
        → exp: is it expired?
        → iss: is it from the expected issuer?
        → aud: is it addressed to this server?
        → scope: does it have the required permission for this endpoint?
Step 5: Extract patient context from "patient" claim → scope the FHIR query
```

**Zero-Downtime Key Rotation (Why `kid` Matters):**

```
Current state: All tokens signed with "key-2026-08"

Rotation event:
  SeymorFHIR generates new key pair → "key-2026-09"
  Publishes BOTH keys in JWKS:
  { "keys": [ {kid: "key-2026-08", ...}, {kid: "key-2026-09", ...} ] }

New tokens: signed with "key-2026-09"
Old tokens: still signed with "key-2026-08" → still verifiable → no service disruption

After old tokens expire (1 hour):
  Remove "key-2026-08" from JWKS
  Done. Zero downtime.
```

---

### 13.6 — How This Is Built in the Seymour Sandbox

This is where the theory connects to your actual code. When a manager asks "walk me through your SMART implementation" — this is what you say.

#### SeymorFHIR — The Authorization Server

| Class | What It Does |
|---|---|
| [`JwtKeyService`](SeymorFHIR/src/main/java/com/healthcare/sandbox/service/JwtKeyService.java) | Generates RSA key pairs, persists them in the `oauth_keys` PostgreSQL table, rotates keys on schedule |
| [`TokenStoreService`](SeymorFHIR/src/main/java/com/healthcare/sandbox/service/TokenStoreService.java) | Stores issued tokens in-memory during their lifetime (in-memory map keyed by token hash) |
| `SmartAuthController` | Handles `/oauth2/authorize` — validates client, generates auth code with launch context |
| `TokenController` | Handles `/oauth2/token` — exchanges auth code for signed JWT |
| `JwksController` | Exposes `/oauth2/jwks` — publishes current active public keys |

**Key architectural decision in your code:**
```
JwtKeyService persists RSA keys in the database (not in memory).
Why: If the server restarts, existing tokens remain verifiable.
     The public key is still in the DB → JWKS endpoint still serves it.
     Without persistence: server restart = all issued tokens become unverifiable = mass logout.
```

#### TerryFoxMemorial — The Resource Server

| Class | What It Does |
|---|---|
| [`SmartFhirSecurityInterceptor`](TerryFoxMemorial/src/main/java/com/terryfox/hospital/interceptor/) | HAPI FHIR interceptor — runs on every FHIR request, verifies JWT before allowing access |
| [`TerryFoxJwksKeyService`](TerryFoxMemorial/src/main/java/com/terryfox/hospital/service/TerryFoxJwksKeyService.java) | Fetches JWKS from SeymorFHIR on startup, caches public keys by kid, refreshes periodically |
| [`TerryFoxRestClientConfig`](TerryFoxMemorial/src/main/java/com/terryfox/hospital/config/TerryFoxRestClientConfig.java) | Configures RestTemplate with 3s connect / 5s read timeouts for JWKS fetch |

**Why a HAPI FHIR interceptor instead of Spring Security?**

HAPI FHIR has its own request lifecycle. Security applied at the `@RestController` level runs BEFORE HAPI processes the request — but `SmartFhirSecurityInterceptor` runs INSIDE HAPI's pipeline, after routing, which means:
- It has access to the resolved FHIR resource type and operation being performed
- It can make scope-aware decisions: "this token has `patient/Observation.read` — is this request for an Observation?"
- Standard Spring Security `@PreAuthorize` can't make that determination

#### Angular SMART App — The Client

| File | What It Does |
|---|---|
| `main.ts` | Handles the full SMART launch flow: detects `iss`+`launch` params, calls discovery, builds authorize URL, handles callback, stores token |
| Callback handler | Extracts `code` + `state`, verifies state matches (CSRF protection), calls token endpoint |
| API calls | Attaches `Authorization: Bearer TOKEN` header to all FHIR requests |

---

### 13.7 — Security Decisions Worth Explaining in an Interview

**1. Why the authorization code is single-use:**
The auth code is immediately invalidated once exchanged for a token. If an attacker intercepts the redirect URL with the code, they have a 30–60 second window to use it — and if the legitimate app exchanges it first, the attacker's attempt gets rejected.

**2. Why `state` parameter is required:**
The `state` value is a random string generated by the client before redirect. After callback, the client verifies the `state` matches. This prevents CSRF attacks where an attacker tricks your browser into completing an OAuth flow you didn't initiate.

**3. Why scope enforcement matters in healthcare:**
If a cancer screening app receives `scope=patient/Observation.read`, it must only access Observations — not the patient's medication history or HIV status. FHIR resources carry sensitive clinical data. Scope enforcement is a patient privacy control, not just an API access control.

**4. Why the patient claim in the token matters:**
The `patient` claim in the JWT tells the resource server which patient the app is authorized for. Even if the token has `patient/*.read`, calling `/fhir/Patient/some-other-patient-id` should be rejected — the token is patient-scoped. This prevents one clinician's app session from accessing data about patients not in their current context.

---

### 13.8 — Interview Q&A — Say This, Not That

---

**Q: "What is SMART on FHIR and why does it exist?"**

> ❌ **Weak:** "It's an authentication standard for FHIR APIs."

> ✅ **Strong:** "SMART on FHIR is a standard that layers OAuth2 onto FHIR to solve two problems. First, it standardizes authentication so any SMART-compliant app can connect to any SMART-compliant EHR without custom integration work. Second, it adds launch context — when a clinician opens an app from inside an EHR, the app instantly receives which patient and encounter is active. Without SMART, the clinician would have to re-search for their patient in every app they open. I've implemented the full SMART Authorization Code flow in my sandbox — the auth server, JWKS endpoint, and the FHIR interceptor that verifies tokens."

---

**Q: "What's the difference between EHR Launch and Standalone Launch?"**

> ✅ **Strong:** "EHR Launch is when the EHR initiates — a clinician clicks on an app from inside their EHR session. The EHR generates a launch token that encodes the patient and encounter context and passes it to the app URL. The app sends this launch token back during authorization, and the auth server decodes it to include patient context in the JWT. Standalone Launch is when the app opens independently — no pre-existing context. The app uses scope `launch/patient` to request patient selection, and the user either selects a patient during login or is shown a patient picker. The technical difference: EHR Launch uses the `launch` scope and a `launch` parameter; Standalone uses `launch/patient` scope and no launch parameter."

---

**Q: "What is a JWKS endpoint and why does a resource server need it?"**

> ✅ **Strong:** "JWKS — JSON Web Key Set — is an endpoint exposed by the authorization server that publishes its RSA public keys. When the resource server receives a JWT, it extracts the key ID from the JWT header, fetches the matching public key from the JWKS endpoint, and uses it to verify the JWT signature. This is how the resource server knows the token is authentic and hasn't been tampered with — without the resource server needing to know the auth server's private key. It also supports key rotation: the auth server can publish multiple keys simultaneously, so when it rotates to a new private key, tokens signed with the old key remain verifiable until they expire."

---

**Q: "What security risks should you watch for in a SMART implementation?"**

> ✅ **Strong:** "Three main areas. First, token storage in the client — JWTs should be stored in memory or secure HTTP-only cookies, never `localStorage`, because XSS attacks can steal localStorage tokens. Second, scope enforcement on the resource server — just because a token is valid doesn't mean it has permission for the specific FHIR resource being requested. I implement scope-aware checking in the HAPI FHIR interceptor. Third, the `state` parameter for CSRF protection — clients must generate a random state, include it in the authorize redirect, and verify it matches in the callback before proceeding with the token exchange."

---

### 13.9 — 📓 Notebook Cards for SMART on FHIR

> Read each card → close document → write in your own words → re-read in 2 days.

---

**CARD S1 — What Problem SMART Solves**

SMART on FHIR exists because before it, every EHR vendor had its own proprietary auth scheme. SMART standardizes OAuth2 for healthcare and adds "launch context" — the ability for an app to know which patient the clinician is currently viewing, without the clinician having to search again.

Two things SMART adds to OAuth2: (1) a discovery endpoint so apps don't hardcode URLs, and (2) a `launch` token that carries clinical context through the auth flow.

---

**CARD S2 — The 5 Steps of Authorization Code Grant**

1. App redirects user to auth server to log in
2. User logs in → auth server redirects back with a short-lived `code`
3. App exchanges `code` for JWT access token (server-to-server, not in browser URL)
4. App receives JWT with patient context in the token payload
5. App calls FHIR server with `Authorization: Bearer JWT`

**Rule:** The code is single-use and expires in ~60 seconds. The token has a longer life (3600 seconds = 1 hour).

---

**CARD S3 — EHR Launch vs Standalone Launch**

EHR Launch: EHR initiates → passes `launch` parameter → clinician usually doesn't see login screen → patient context automatic.
Scope: `launch`

Standalone Launch: App initiates → no launch parameter → user always logs in → user selects patient during auth.
Scope: `launch/patient`

Memory hook: **EHR Launch has a `launch` parameter. Standalone has `launch/patient` scope. The word "patient" in the scope tells you who has to pick the patient — the user, not the EHR.**

---

**CARD S4 — JWT Structure (3 Parts)**

Header.Payload.Signature — separated by dots.

Header: algorithm (`RS256`) + key ID (`kid`)
Payload: issuer, subject, audience, expiry, scope, patient, encounter
Signature: signed with auth server's private RSA key

**Rule:** Verify `exp` (not expired), `iss` (trusted issuer), `aud` (this server is the audience), `scope` (has permission for this operation).

**Gotcha:** A valid signature means the token is authentic. It does NOT mean the token has the right scope. Always check scope separately.

---

**CARD S5 — JWKS and Key Rotation**

JWKS = auth server publishes its RSA public key at a known URL.
Resource server fetches JWKS → caches by `kid` → uses matching key to verify JWT signature.

Key rotation: auth server generates new key pair → publishes BOTH old and new in JWKS → uses new key for new tokens → old tokens still verifiable with old key until they expire → then removes old key. Zero downtime.

**Rule:** The `kid` in the JWT header must match a `kid` in the JWKS. If no match found → reject with 401.

---

**CARD S6 — Scope Format Rule**

`[context]/[ResourceType].[permission]`

- `patient/*.read` = read ANY resource for current patient
- `patient/Observation.read` = read ONLY Observations for current patient
- `user/*.read` = read any resource across all of user's patients
- `offline_access` = get a refresh token for long sessions
- `openid profile fhirUser` = OIDC identity info about the logged-in user

**Gotcha:** `patient/*.read` does NOT give access to other patients' data. "patient" in the scope means the patient from the launch context — not all patients.

---



## 14. End-to-End API Testing & Curl Command Reference

### 14.1 Terry Fox Memorial HAPI FHIR Server (Java - Port 8085)

```bash
# 1. HAPI FHIR Capability Statement
curl -s -H "Accept: application/fhir+json" http://localhost:8085/fhir/metadata | jq .

# 2. Fetch Patients (HAPI FHIR R4 Bundle)
curl -s -H "Accept: application/fhir+json" http://localhost:8085/fhir/Patient | jq .

# 3. Fetch mCODE Cancer Staging Conditions
curl -s -H "Accept: application/fhir+json" http://localhost:8085/fhir/Condition | jq .

# 4. Fetch Clinical Trial Subject Enrollments
curl -s -H "Accept: application/fhir+json" http://localhost:8085/fhir/ResearchSubject | jq .

# 5. Ingest Raw HL7 v2 Pathology Report (ORU^R01)
curl -s -X POST -H "Content-Type: text/plain" \
  --data $'MSH|^~\\&|PATH_LAB|VGH|TERRYFOX|ONCOLOGY|20260802||ORU^R01|MSG01|P|2.5.1\nPID|1||9234567897^^^BC_PHN||Wong^Eleanor||19800512|F\nOBR|1||PATH-901|HER2^HER2 FISH^LN\nOBX|1|ST|HER2^HER2 IHC||HER2 Positive (3+)' \
  http://localhost:8085/api/terryfox/hl7 | jq .

# 6. Trigger Multi-Hospital Regional Interoperability Sync
curl -s -X POST http://localhost:8085/api/terryfox/sync/regional | jq .
```

### 14.2 Seymour Regional EHR Server (Java - Port 8090)

```bash
# 1. Fetch All Active Patients
curl http://localhost:8090/api/fhir/Patient | jq .

# 2. Search Patient by Name
curl "http://localhost:8090/api/fhir/Patient?name=Jenkins" | jq .

# 3. Patient Demographic Match ($match)
curl -X POST http://localhost:8090/api/fhir/Patient/\$match \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "Parameters",
    "parameter": [
      {
        "name": "patient",
        "resource": {
          "resourceType": "Patient",
          "name": [{ "family": "Chen", "given": ["Margaret"] }],
          "birthDate": "1948-03-12"
        }
      }
    ]
  }' | jq .
```

### 14.3 Langley General Gateway (C# .NET 10 - Port 8083)

```bash
# 1. Fetch C# Gateway Patients
curl http://localhost:8083/fhir/Patient | jq .

# 2. Ingest Native HL7 v2 Message in C#
curl -X POST http://localhost:8083/api/langleygeneral/hl7 \
  -H "Content-Type: text/plain" \
  -d $'MSH|^~\\&|LANGLEY_GENERAL|MAIN_FACILITY|GATEWAY|GATEWAY_FACILITY|20260721100000||ADT^A01^ADT_A01|MSG2001|P|2.4\nPID|1||MRN-90001^^^MRN||Smith^John||19850615|M|||100 Fraser Hwy^^Langley^BC^V3A 4X6^CA||604-555-9988||||||9234567897' | jq .
```

---

## 15. Architectural Resilience & Fault Tolerance: The Bulkhead Pattern

In enterprise healthcare IT systems (such as high-throughput hospital gateways, EHR interfaces, and emergency alert processing), **system availability and fault isolation** are critical to patient safety. A single malfunctioning downstream service or a surge in heavy report queries must never crash the entire API server.

```
                              +-------------------------------------------------------------+
                              |              Healthcare API Ingress Gateway                 |
                              +------------------------------+------------------------------+
                                                             |
                     +---------------------------------------+---------------------------------------+
                     |                                       |                                       |
                     v                                       v                                       v
      +------------------------------+        +------------------------------+        +------------------------------+
      | Bulkhead Pool A: Emergency   |        | Bulkhead Pool B: Patient     |        | Bulkhead Pool C: Heavy Lab   |
      | Alerts & Vitals (High Pri)   |        | Search & CRUD (Normal)       |        | Export Reports (Heavy/Slow)  |
      | Dedicated Threads: 50        |        | Dedicated Threads: 30        |        | Dedicated Threads: 20        |
      +--------------+---------------+        +--------------+---------------+        +--------------+---------------+
                     |                                       |                                       |
                     v                                       v                                       v
      [Emergency Alert API - 10ms]            [Patient Query API - 50ms]              [Heavy Lab PDF Export - 5s]
```

### 15.1 Real-World Anchor: Nautical Bulkhead Compartments
The **Bulkhead Pattern** is named after the physical watertight partitions inside a ship's hull. If a rock breaches Section A of a cargo ship, water fills Section A, but the **bulkhead partition walls prevent water from spilling into Sections B, C, or D**. Section A is damaged, but **the ship stays afloat**.

In software, a Bulkhead **isolates execution resources (thread pools, memory, connection pools)** into distinct compartments so that an outage or thread starvation in one component cannot drown the rest of the application.

---

### 15.2 Problem Scenario: Thread Starvation in Healthcare Gateways

Consider an API server with a unified thread pool of 100 threads handling three endpoints:
1. `GET /api/fhir/Patient` (Fast query: ~20ms)
2. `POST /api/alerts/emergency` (Critical ER alert: ~10ms)
3. `POST /api/reports/heavy-lab-export` (Slow PDF generation: ~5,000ms)

#### Without Bulkhead (Failure Cascade):
If 100 clinicians trigger heavy lab exports simultaneously:
- All 100 threads become occupied waiting for slow DB/PDF execution.
- An incoming **Emergency ER Alert** arrives at the gateway... **and hangs or gets rejected with `504 Gateway Timeout`**. The entire hospital API is offline because one feature consumed 100% of server capacity.

#### With Bulkhead Isolation:
By partitioning capacity into dedicated thread pools:
- **Emergency Alerts Pool:** Max 50 threads reserved
- **Patient CRUD Pool:** Max 30 threads reserved
- **Heavy Lab Export Pool:** Max 20 threads capped

When 500 requests flood the **Heavy Lab Export** endpoint:
- Only the 20 threads in Pool C fill up. Extra export requests fail gracefully with HTTP `429 Too Many Requests` or `503 Service Unavailable`.
- **Emergency Alerts and Patient Queries continue operating at 100% full speed.** The application stays afloat!

---

### 15.3 Implementation Reference: Java (Resilience4j) & C# (.NET Polly)

#### A. Java Spring Boot Implementation (Resilience4j)
```java
package com.healthcare.sandbox.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class HeavyReportService {

    // Caps execution of heavy reports to a dedicated bulkhead pool
    @Bulkhead(name = "heavyReportPool", fallbackMethod = "fallbackHeavyReport")
    public Map<String, Object> generateLabExportPdf(Long patientId) {
        // Heavy processing / PDF generation logic
        simulateHeavyDbQuery();
        return Map.of("status", "SUCCESS", "patientId", patientId);
    }

    // Graceful fallback when the bulkhead pool is full
    public Map<String, Object> fallbackHeavyReport(Long patientId, Throwable t) {
        return Map.of(
            "status", "DEGRADED",
            "message", "Heavy export system busy. Request queued for background execution.",
            "patientId", patientId
        );
    }

    private void simulateHeavyDbQuery() {
        try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

`application.yml` Bulkhead Configuration:
```yaml
resilience4j.thread-pool-bulkhead:
  instances:
    heavyReportPool:
      maxThreadPoolSize: 20
      coreThreadPoolSize: 10
      queueCapacity: 50
```

---

#### B. C# .NET Implementation (Polly Bulkhead Policy)
```csharp
using Polly;
using Polly.Bulkhead;

public class HealthcareGatewayResilience
{
    private readonly AsyncBulkheadPolicy _heavyReportBulkhead;

    public HealthcareGatewayResilience()
    {
        // Max 10 concurrent heavy report executions, max 5 queued actions
        _heavyReportBulkhead = Policy.BulkheadAsync(
            maxParallelization: 10,
            maxQueuingActions: 5,
            onBulkheadRejectedAsync: context => {
                Console.WriteLine("Bulkhead full! Rejecting heavy report request to preserve API capacity.");
                return Task.CompletedTask;
            }
        );
    }

    public async Task<IResult> ExecuteHeavyExportAsync(Func<Task<IResult>> action)
    {
        try 
        {
            return await _heavyReportBulkhead.ExecuteAsync(action);
        }
        catch (BulkheadRejectedException)
        {
            return Results.Json(new { 
                error = "Server Busy", 
                message = "Lab report export capacity reached. Please try again shortly." 
            }, statusCode: 429);
        }
    }
}
```

---

### 15.4 Comparison Matrix: Resilience Patterns

| Pattern | Primary Goal | Real-World Metaphor | Implementation Tool |
| :--- | :--- | :--- | :--- |
| **Bulkhead** | Isolate resource capacity into separate pools so failure in one area doesn't drown the rest. | Watertight compartments on a ship / Separate fuse boxes in a house. | Resilience4j `@Bulkhead` / Polly `Policy.BulkheadAsync` |
| **Circuit Breaker** | Detect downstream failure and trip open to stop calling a broken service, allowing it to recover. | Electrical circuit breaker tripping during a power surge. | Resilience4j `@CircuitBreaker` / Polly `Policy.Handle<Exception>()` |
| **Rate Limiter** | Restrict total incoming request frequency over a time window to prevent API abuse. | Bouncer at the front door checking entry speed. | Bucket4j / Spring Cloud Gateway RateLimiter / ASP.NET Core RateLimiting |
| **Retry with Backoff** | Automatically re-attempt transient network/database errors with exponential delay. | Dialing a busy phone number again after 5s, 10s, 20s. | Resilience4j `@Retry` / Polly `Policy.Handle().WaitAndRetryAsync()` |

---

## 15. Reliable HL7 Message Audit, MSH-10 Idempotency & Tracing

In enterprise hospital interface networks (e.g. **Philips IntelliBridge**, **Cerner OpenEngine**, **Mirth Connect**), network glitches or server timeouts frequently trigger interface retries. Without strict idempotency protection, a re-sent HL7 `ADT^A01` message can create duplicate patient demographics and duplicate inpatient encounters.

### 15.1 Message Lifecycle & State Machine

Seymour Sandbox implements full audit tracking (`Hl7AuditLog.java`) following a 5-stage state machine:

```
[ INBOUND HL7 ] ──► ( RECEIVED ) ──► ( VALIDATED ) ──► ( TRANSFORMED ) ──► ( DELIVERED )
                                                                 │
                                                                 └─────► ( FAILED )
```

| Lifecycle State | Description | DB Table Status |
| :--- | :--- | :--- |
| **`RECEIVED`** | Raw HL7 string received over HTTP or MLLP; unique correlation ID generated. | `RECEIVED` |
| **`VALIDATED`** | MSH segment format verified; MSH-10 Control ID and SHA-256 payload hash checked for duplicates. | `VALIDATED` |
| **`TRANSFORMED`**| Demographics extracted; PHN Modulus-11 validated; FHIR Patient & AdtEvent constructed. | `TRANSFORMED` |
| **`DELIVERED`** | Patient and AdtEvent successfully persisted in PostgreSQL database. | `DELIVERED` |
| **`FAILED`** | Parsing error, checksum failure, or identity match conflict halted processing. | `FAILED` |

### 15.2 MSH-10 & SHA-256 Idempotency Implementation

```java
// Check MSH-10 Control ID Idempotency FIRST
Optional<Hl7AuditLog> existingControlLog = auditLogRepo.findByMessageControlId(messageControlId);
if (existingControlLog.isPresent()) {
    log.warn("HL7 Idempotency Rejection: MSH-10 Message Control ID [{}] already processed.", messageControlId);
    throw new IllegalArgumentException("Duplicate MSH-10 Message Control ID rejected: " + messageControlId);
}

// Check Payload SHA-256 Hash Idempotency SECOND
Optional<Hl7AuditLog> existingHashLog = auditLogRepo.findByPayloadHash(payloadHash);
if (existingHashLog.isPresent()) {
    Hl7AuditLog prevLog = existingHashLog.get();
    throw new IllegalArgumentException("Duplicate HL7 payload rejected. Previously processed under Correlation ID: " + prevLog.getCorrelationId());
}
```

---

## 16. Patient Identity Conflict Resolution & PENDING_REVIEW Queue

Patient safety is the top priority in regional health networks. When an inbound ADT message arrives with an ambiguous demographic match (e.g. matching name and DOB but a different MRN or misspelled surname), **silently overwriting patient history or creating a duplicate record violates clinical safety**.

### 16.1 Multi-Field Match Scoring Engine

Seymour calculates a weighted demographic match score ($S \in [0.0, 1.0]$):

$$S = S_{\text{LastName}} (0.35) + S_{\text{FirstName}} (0.25) + S_{\text{DOB}} (0.40)$$

| Match Score Range | Classification | Action Taken |
| :--- | :--- | :--- |
| **$S \ge 0.85$** | High Confidence Match | Automatically associate data with existing Patient record. |
| **$0.35 \le S < 0.85$** | **Ambiguous Conflict** | **Halt Patient creation**. Save `PatientMatchReview` in `PENDING_REVIEW` queue. |
| **$S < 0.35$** | No Match | Safely register new Patient record in database. |

### 16.2 Conflict Resolution Workflow

1. Inbound HL7 ADT message triggers match score calculation.
2. Score $0.60$ triggers `PENDING_REVIEW` state in `PatientMatchReview` entity.
3. Patient creation is **immediately halted**, throwing an exception.
4. Health Information Management (HIM) analysts inspect pending conflicts via `GET /api/fhir/Patient/match-reviews`.
5. HIM analyst approves the record via `POST /api/fhir/Patient/match-reviews/{id}/approve`, promoting the record to `MANUALLY_APPROVED` and registering the Patient safely.

---

## 17. Persistent RSA Key Stores & Zero-Downtime Key Rotation (`kid` Cache Eviction)

In enterprise healthcare networks, OAuth2 Identity Providers must support continuous, zero-downtime key rotation without dropping clinical transactions.

### 17.1 Persistent Key Management (`oauth_keys`)
Private/Public RSA keypairs are serialized as PKCS#8 / X.509 PEM strings and stored in PostgreSQL `oauth_keys`:

```sql
CREATE TABLE oauth_keys (
    id BIGSERIAL PRIMARY KEY,
    key_id VARCHAR(100) NOT NULL UNIQUE,
    private_key_pem TEXT NOT NULL,
    public_key_pem TEXT NOT NULL,
    algorithm VARCHAR(20) NOT NULL DEFAULT 'RS256',
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);
```

### 17.2 Dynamic `kid` Cache Eviction Algorithm
Remote microservices (e.g. Terry Fox HAPI FHIR Node) verify RS256 Bearer JWTs statelessly using public keys published at `http://localhost:8090/.well-known/jwks.json`.

```
[ Incoming Bearer Token ] ──▶ Inspect Header `kid`: "seymour-key-1786215570"
                                     │
                        ┌────────────┴────────────┐
                        ▼                         ▼
             [ `kid` in RAM Cache? ]   [ `kid` Missing from Cache? ]
                        │                         │
                        ▼                         ▼
              (Verify Signature)       [ Log `[JWKS_CACHE_EVICT]` ]
                                                  │
                                                  ▼
                                       [ HTTP GET `/.well-known/jwks.json` ]
                                                  │
                                                  ▼
                                       [ Update RAM Cache & Verify ]
```

---

## 18. Cross-Hospital EMPI Identity Reconciliation Engine

When a clinical portal queries regional specialty nodes (e.g., Terry Fox Oncology), cross-hospital identity linking is performed using unique patient identifiers (e.g., BC Personal Health Number).

### 18.1 Demographic Discrepancy Scoring
When demographic differences occur (e.g. a 3-day Date of Birth discrepancy: `1948-03-12` vs `1948-03-15`), the reconciliation engine computes a Match Confidence Score ($M \in [0, 100]$):

$$M = 100 - \Delta_{\text{DOB}} (12) - \Delta_{\text{Name}} (5)$$

1. **High Match ($M \ge 95\%$):** Clean cross-hospital rendering.
2. **Identity Conflict ($M < 95\%$):** Displays amber **EMPI Identity Conflict Warning Banner** listing exact field deltas.
3. **Audit Action:** Renders interactive **"Flag for EMPI Audit Review"** action button to queue the record for administrative review across regional health nodes.



