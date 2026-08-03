# FHIR & Healthcare Integration Engineering Masterclass (Java & C#)

Welcome to the **Healthcare Sandbox Integration & Interoperability Masterclass Guide**. This document provides a comprehensive, deep-dive architectural reference for integration engineers, health system software developers, and interface analysts working with **HL7 v2**, **FHIR R4**, **mCODE Oncology Data Models**, **HAPI FHIR Java Framework**, **C# .NET 10 Web APIs**, **SMART on FHIR OAuth2**, **mTLS & System-to-System Token Security**, **Mirth Connect Middleware**, **BC Longitudinal Record Access (LRA)**, and enterprise Canadian/BC EHR standards (**MEDITECH Expanse**, **Cerner Millennium**, and **Epic**).

---

## 📚 Table of Contents

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

---

## ⚠️ Synthetic Data & Open Source Developer Disclaimer
> **This repository and guide represent an open-source educational developer sandbox designed exclusively for learning healthcare software engineering, HL7 v2 messaging, HAPI FHIR R4 standards, and BC identity governance (Modulus-11 PHN validation). All patient names, Personal Health Numbers (PHNs), Medical Record Numbers (MRNs), diagnoses, lab results, and clinical trial data are 100% synthetic, fictitious, and artificially generated. No real Personal Health Information (PHI) is used or stored.**

---

## 1. Architectural Overview & Ecosystem Topology

The sandbox simulates a multi-hospital regional health authority ecosystem consisting of **four distinct microservices** and an integration engine middleware:

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
  |       Port: 8081 | DB: langley_db                  |      |       Port: 8083 | DB: langley_general_db             |
  +----------------------------------------------------+      +-------------------------------------------------------+
                               ^                                                          ^
                               |                                                          |
                               +------------------------- Mirth Connect ------------------+
                                                 (TCP MLLP Port 9085 -> Webhook)
```

### Microservice Roles & Architectural Highlights:

1. **Seymour FHIR Server (`SeymorFHIR` — Java / Port 8090):**
   * Primary Regional EHR Repository.
   * Exposes RESTful FHIR R4 resources (`Patient`, `Encounter`, `Observation`, `AllergyIntolerance`, `MedicationRequest`, `DocumentReference`).
   * Implements atomic FHIR `transaction` bundles, SMART-on-FHIR OAuth2 server (`/oauth2/authorize`, `/oauth2/token`), and Patient Master Index (`$match`).
2. **Terry Fox Memorial Cancer Hospital (`TerryFoxMemorial` — Java / Port 8085):**
   * Specialized Oncology & Clinical Trials Node powered by the official **HAPI FHIR R4 Engine (`ca.uhn.fhir.rest.server.RestfulServer`)**.
   * Implements mCODE (Minimal Common Oncology Data Elements) TNM staging, clinical trial protocols (`ResearchStudy` / `ResearchSubject`), NGS genomic reports (`DiagnosticReport`), HL7 v2 pathology ingestion (`ORU^R01`/`MDM^T02`), and the Multi-Hospital Regional Sync Pipeline (`RegionalSyncService`).
3. **Langley General Gateway (`LangleyGeneralGateway` — C# .NET 10 / Port 8083):**
   * Enterprise C# registration gateway and FHIR API service.
   * Handles pipe-delimited HL7 v2 ingestion (`MSH`, `PID`, `PV1`, `OBX`), C# FHIR R4 bundle processing, timezone-safe `DateOnly` birthdate binding, and C# Modulus-11 PHN validation.
4. **Langley Children's Hospital Backend (`langley-backend` — Java / Port 8081):**
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
               │                                                                      │
               └─── 4. FHIR REST Request (Authorization: Bearer <token>) ─────────────►│
```

### Key Security Layers:
1. **Certificate Pre-Registration**: The client certificate's Common Name (CN) and public key fingerprint are pre-registered and whitelisted in the developer/provincial portal.
2. **Mutual TLS (mTLS)**: Handshake validates both server identity and client certificate before HTTP traffic is processed.
3. **OAuth2 Client Credentials (RFC 7523)**: Authenticates using a signed JWT assertion (`RS256`), granting scoped access (`system/CoverageEligibilityResponse.read`).

---

## 6. Enterprise EHR Architectures: MEDITECH, Cerner Millennium, & Epic

Understanding how the "Big 3" enterprise Health Information Systems (HIS) operate in Canadian health authorities is essential for integration software engineers:

### 6.1 MEDITECH (Fraser Health & Interior Health Primary EHR)
* **Architecture**: Enterprise EHR platform (Expanse / MAGIC) powering Fraser Health emergency, registration, laboratory, and inpatient wards.
* **Engineering Responsibilities**:
  1. **HL7 v2 Interface Management**: Configuring `ADT^A01/A04/A08` demographic feeds and `ORU^R01` lab/radiology result interfaces.
  2. **MEDITECH Data Repository (DR) & T-SQL**: Querying MEDITECH's SQL Server Data Repository (DR) tables (`AdmPatients`, `PharMedications`, `LALibrary`) using T-SQL for clinical reporting and census dashboards.
  3. **MEDITECH Expanse REST / FHIR APIs**: Connecting external applications via RESTful FHIR R4 endpoints.

### 6.2 Cerner Millennium / CST Cerner (PHSA, VCH, PHC)
* **Architecture**: The regional EHR deployed under BC's **Clinical Systems Transformation (CST)** across PHSA (BC Cancer, BC Children's, BC Women's), VCH, and PHC.
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

## 13. SMART on FHIR OAuth2 Authentication & Launch Context

The sandbox simulates the **SMART App Launch Framework** (OAuth2 Authorization Code Grant with launch context):

1. **Authorization Request (`GET /oauth2/authorize`):**
   - Parameters: `response_type=code`, `client_id=my_clinical_app`, `redirect_uri=http://localhost:3000/callback`.
   - Returns: `302 Redirect` with authorization code.
2. **Token Exchange (`POST /oauth2/token`):**
   - Content-Type: `application/x-www-form-urlencoded`.
   - Returns:
     ```json
     {
       "access_token": "simulated_access_token_abc123",
       "token_type": "Bearer",
       "expires_in": 3600,
       "scope": "launch/patient patient/*.read",
       "patient": "1"
     }
     ```

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
