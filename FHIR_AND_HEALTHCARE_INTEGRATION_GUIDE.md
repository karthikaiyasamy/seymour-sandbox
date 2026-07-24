# FHIR & Healthcare Integration Engineering Masterclass (Java & C#)

Welcome to the **Healthcare Sandbox Integration & Interoperability Guide**. This guide provides a comprehensive, hands-on architectural reference for integration engineers, health system software developers, and interface analysts working with **HL7 v2**, **FHIR R4**, **SMART on FHIR**, and Canadian/BC healthcare data standards.

---

## Table of Contents

1. [Architectural Overview & Ecosystem Topology](#1-architectural-overview--ecosystem-topology)
2. [HL7 v2 vs FHIR R4: Core Concepts & Protocols](#2-hl7-v2-vs-fhir-r4-core-concepts--protocols)
3. [Canadian Baseline (CA Baseline) & BC Personal Health Number (PHN) Standards](#3-canadian-baseline-ca-baseline--bc-personal-health-number-phn-standards)
4. [Java Integration Engineering Reference (Seymour FHIR & Langley Backend)](#4-java-integration-engineering-reference-seymour-fhir--langley-backend)
5. [C# .NET 10 Integration Engineering Reference (Langley General Gateway)](#5-c-net-10-integration-engineering-reference-langley-general-gateway)
6. [Integration Engine Pipeline (Mirth Connect & Webhooks)](#6-integration-engine-pipeline-mirth-connect--webhooks)
7. [SMART on FHIR OAuth2 Authentication & Launch Context](#7-smart-on-fhir-oauth2-authentication--launch-context)
8. [End-to-End API Testing & Curl Command Reference](#8-end-to-end-api-testing--curl-command-reference)

---

## 1. Architectural Overview & Ecosystem Topology

The sandbox consists of **three microservices** representing distinct health authority nodes and clinical interfaces:

```
                      +------------------------------------------+
                      |         Seymour Regional EHR             |
                      |    (Java / Spring Boot / HAPI FHIR)       |
                      |  Port: 8090 | DB: seymour_db (PostgreSQL)  |
                      +--------------------+---------------------+
                                           |
                                           | HL7 v2 (TCP/MLLP)
                                           v
                      +--------------------+---------------------+
                      |          Mirth Connect Engine            |
                      |    (MLLP Receiver -> HTTP Forwarder)     |
                      |  Port: 9085 -> Webhooks                  |
                      +----------+-------------------+-----------+
                                 |                   |
               HTTP Webhook Sync |                   | HTTP HL7/FHIR Ingest
                                 v                   v
   +-----------------------------+----+   +----------+----------------------------+
   |   Langley Children's Backend     |   |       Langley General Gateway          |
   |      (Java / Spring Boot)        |   |       (C# / .NET 10 Web API)          |
   |  Port: 8081 | DB: langley_db     |   |  Port: 8083 | DB: langley_general_db    |
   +----------------------------------+   +---------------------------------------+
```

### Microservice Roles:
1. **Seymour FHIR Server (`SeymorFHIR` - Java):** Acts as the primary Regional EHR Repository, serving FHIR R4 resources (`Patient`, `Encounter`, `Observation`, `AllergyIntolerance`, `MedicationRequest`, `DocumentReference`), executing atomic FHIR `Bundle` transactions, and publishing TCP/MLLP HL7 streams.
2. **Langley Children's Hospital Backend (`langley-backend` - Java):** Specialized pediatric hospital backend consuming webhook synchronization payloads (immunizations, lab results, allergies) emitted downstream by the integration pipeline.
3. **Langley General Gateway (`LangleyGeneralGateway` - C# .NET 10):** Enterprise C# demographics gateway and FHIR API service that handles native pipe-delimited HL7 v2 ingestion (`MSH`, `PID`, `PV1`, `OBX`), FHIR R4 Bundle processing, and BC PHN validation.

---

## 2. HL7 v2 vs FHIR R4: Core Concepts & Protocols

Health integration engineers routinely bridge legacy **HL7 v2** pipe-delimited messages and modern **FHIR R4** JSON RESTful APIs.

### 2.1 Comparison Matrix

| Dimension | HL7 v2 (Legacy / Enterprise EHR) | FHIR R4 (Modern Interoperability) |
| :--- | :--- | :--- |
| **Data Format** | Pipe-delimited text (`MSH\|^~\&\|...`) | JSON / XML / Turtle (`"resourceType": "Patient"`) |
| **Transport Layer** | TCP/IP sockets with MLLP framing (`0x0B ... 0x1C 0x0D`) | HTTP/S REST, Webhooks, Websockets |
| **Data Structure** | Segments (`PID`, `PV1`, `OBX`) & Fields | Domain Resources (`Patient`, `Observation`, `Encounter`) |
| **Trigger Mechanism** | Event-driven (e.g. `ADT^A01` on patient admission) | RESTful CRUD (`GET`, `POST`, `PUT`, `DELETE`) & Subscriptions |
| **Terminology** | HL7 Tables, local code sets | LOINC, SNOMED CT, RxNorm, UCUM |
| **Validation** | Segment length & delimiter checks | Structural FHIR schemas & `OperationOutcome` responses |

### 2.2 HL7 v2 Segment to FHIR R4 Resource Cheat Sheet

| HL7 v2 Segment | Standard Meaning | Corresponding FHIR R4 Resource | Key Fields / Purpose |
| :--- | :--- | :--- | :--- |
| **`PID`** | Patient Identification | **`Patient`** | Demographics (MRN, PHN, Name, DOB, Sex, Address) |
| **`PV1`** | Patient Visit | **`Encounter`** | Class (Inpatient/Outpatient), Ward, Room, Bed, Attending Doctor |
| **`DG1`** | Diagnosis | **`Condition`** (or `Encounter.diagnosis`) | Diagnosis Description & Code (e.g. ICD-10 `E11.69`) |
| **`OBX`** | Observation / Lab Result / Vitals | **`Observation`** | Lab Test Values (HbA1c, Glucose), Vitals, Units, Abnormal Flags |
| **`AL1`** | Allergy Information | **`AllergyIntolerance`** | Allergen (Penicillin, Peanuts), Category, Severity, Reaction |
| **`RXA` / `RXO`** | Pharmacy Admin / Order | **`MedicationRequest` / `MedicationAdministration`** | Medication Name, RxNorm Code, Dosage, Frequency, Route |
| **`NK1`** | Next of Kin | **`Patient.contact`** or **`RelatedPerson`** | Emergency Contacts, Primary Caregivers, Relationships |

### 2.3 HL7 v2 Segment Structure Example (ADT^A01 Admit)
```hl7
MSH|^~\&|SEYMOUR_EHR|VANCOUVER_GH|REC_APP|REC_FAC|20260721093000||ADT^A01^ADT_A01|MSG1001|P|2.4
PID|1||MRN-10001^^^MRN||Chen^Margaret||19480312|F|||145 Maple Street^^Vancouver^BC^V5K 1A1^CA||604-555-0101||||||BC9001234567
PV1|1|I|4 North^412^A^Vancouver General Hospital||||Dr. Sarah Park|||||||||||VN-2024-88001|||||||||||||||||||||||||20260711093000
DG1|1|I10|E11.69^Type 2 diabetes mellitus with hyperglycemia^ICD-10|||A
OBX|1|NM|4548-4^HbA1c^LN||9.4|%|4.0-6.0|H|||F
```

### 2.3 Equivalent FHIR R4 Decomposition (Resources & Bundle)

Unlike HL7 v2 which packages demographics, admission, diagnosis, and lab results into a single pipe-delimited packet, FHIR R4 decomposes the data into discrete, normalized resources linked by references:

#### 1. `PID` Segment → `Patient` Resource
```json
{
  "resourceType": "Patient",
  "id": "1",
  "identifier": [
    {
      "use": "official",
      "system": "http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn",
      "value": "BC9001234567"
    }
  ],
  "name": [{ "family": "Chen", "given": ["Margaret"] }],
  "gender": "female",
  "birthDate": "1948-03-12"
}
```

#### 2. `PV1` Segment → `Encounter` Resource
```json
{
  "resourceType": "Encounter",
  "id": "enc-88001",
  "status": "in-progress",
  "class": { "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code": "IMP", "display": "inpatient encounter" },
  "subject": { "reference": "Patient/1", "display": "Margaret Chen" },
  "location": [{ "location": { "display": "Vancouver General Hospital - 4 North, Room 412, Bed A" } }]
}
```

#### 3. `DG1` Segment → `Condition` Resource (Diagnosis)
```json
{
  "resourceType": "Condition",
  "id": "cond-101",
  "clinicalStatus": { "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/condition-clinical", "code": "active" }] },
  "code": {
    "coding": [{ "system": "http://hl7.org/fhir/sid/icd-10", "code": "E11.69", "display": "Type 2 diabetes mellitus with hyperglycemia" }],
    "text": "Type 2 diabetes mellitus with hyperglycemia"
  },
  "subject": { "reference": "Patient/1" }
}
```

#### 4. `OBX` Segment → `Observation` Resource (Lab Result)
```json
{
  "resourceType": "Observation",
  "id": "obs-201",
  "status": "final",
  "category": [{ "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/observation-category", "code": "laboratory" }] }],
  "code": {
    "coding": [{ "system": "http://loinc.org", "code": "4548-4", "display": "Hemoglobin A1c/Hemoglobin.total in Blood" }],
    "text": "HbA1c"
  },
  "subject": { "reference": "Patient/1" },
  "valueQuantity": { "value": 9.4, "unit": "%", "system": "http://unitsofmeasure.org", "code": "%" },
  "interpretation": [{ "coding": [{ "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation", "code": "H", "display": "High" }] }]
}
```

---

## 3. Canadian Baseline (CA Baseline) & BC Personal Health Number (PHN) Standards

In Canadian health informatics (PHSA, Fraser Health, Provincial Client Registries), interoperability specifications mandate strict conformance to Canadian extensions:

### 3.1 BC Personal Health Number (PHN) System URI
BC PHNs are registered under the national extension naming system URI:
`http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn`

### 3.2 BC PHN Modulus-11 Validation Algorithm
A valid BC PHN is **10 digits**, starts with a **9**, and passes the Modulus-11 check digit calculation across digits 2 to 9:

$$\text{Sum} = \sum_{i=2}^{9} d_i \times w_{i-1}$$

Where weights $w = [2, 4, 8, 5, 10, 9, 7, 3]$.
The remainder is $R = \text{Sum} \pmod{11}$.
The calculated check digit is $11 - R$.

#### Implementation Reference (Java & C#):
- **Java:** `com.healthcare.sandbox.util.PhnValidator.isValidBCPHN(phn)`
- **C#:** `LangleyGeneralGateway.Utils.PhnValidator.IsValidBCOnlyPHN(phn)`

---

## 4. Java Integration Engineering Reference (Seymour FHIR & Langley Backend)

### 4.1 Architecture & Tech Stack
- **Framework:** Spring Boot 3.x with Java 21
- **Database:** PostgreSQL (with H2/SQLite fallback options)
- **FHIR Model Parser:** Custom FHIR Jackson mapping + HAPI FHIR Core structures

### 4.2 Key Features Implemented:
1. **FHIR `AllergyIntolerance` API (`/api/fhir/AllergyIntolerance`):**
   - Implements SNOMED-CT codes, category (`medication`, `food`, `environment`), criticality (`low`, `high`), and clinical status (`active`, `resolved`).
2. **FHIR `Observation` API (`/api/fhir/Observation`):**
   - Implements LOINC codes (e.g. `4548-4` HbA1c, `2339-0` Glucose, `8867-4` Heart rate, `10839-9` Troponin I) with UCUM units (`%`, `mmol/L`, `beats/min`, `ng/mL`).
3. **FHIR Bundle Transaction Engine (`POST /api/fhir`):**
   - Executes atomic `@Transactional` processing of incoming FHIR `transaction` bundles containing `Patient`, `Observation`, and `AllergyIntolerance` entries.
   - Returns standard FHIR `transaction-response` bundles with `201 Created` / `200 OK` HTTP status codes.
4. **Client Registry Services (CRS) `$match` Operation (`POST /api/fhir/Patient/$match`):**
   - Performs deterministic demographic matching against family name, given name, and birth date.

---

## 5. C# .NET 10 Integration Engineering Reference (Langley General Gateway)

### 5.1 Architecture & Tech Stack
- **Framework:** ASP.NET Core Web API (.NET 10)
- **ORM:** Entity Framework Core (Code-First Migrations)
- **Database:** PostgreSQL (`langley_general_db`)

### 5.2 Key Features Implemented:
1. **Timezone-Safe Date Processing:**
   - Domain entity uses C# `DateOnly` for `DateOfBirth` to prevent UTC / Pacific timezone shifting errors during serialization.
2. **Native HL7 v2 Ingest Controller (`POST /api/langleygeneral/hl7`):**
   - Utilizes `Utils/Hl7Parser.cs` to parse pipe-delimited text (`MSH`, `PID`, `PV1`, `OBX`), extract patient demographics & attached lab observations, validate BC PHN, and upsert records into PostgreSQL.
3. **C# FHIR R4 Controllers (`/fhir/Patient`, `/fhir/Observation`, `/fhir`):**
   - Provides side-by-side .NET implementations of FHIR REST endpoints and Bundle transactions matching the Java Seymour server interface.

---

## 6. Integration Engine Pipeline (Mirth Connect & Webhooks)

In enterprise hospital integration, **Mirth Connect** (or Rhapsody / Cloverleaf) acts as the central router:

1. **TCP MLLP Ingestion:** Mirth listens on TCP port `9085`.
2. **Parsing & Mapping:** Converts raw HL7 `PID-3` (MRN), `OBX-3` (Test Code), `OBX-5` (Value) segments into JSON DTOs.
3. **HTTP Webhook Dispatch:** Posts JSON payloads to downstream subscriber endpoints:
   - `POST http://localhost:8081/api/langley/pediatric/sync`
   - `POST http://localhost:8081/api/langley/pediatric/allergy-sync`

---

## 7. SMART on FHIR OAuth2 Authentication & Launch Context

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

## 8. End-to-End API Testing & Curl Command Reference

### 8.1 Seymour FHIR Server (Java - Port 8090)

```bash
# 1. Fetch All Active Patients
curl http://localhost:8090/api/fhir/Patient | jq .

# 2. Lookup Patient by BC PHN
curl "http://localhost:8090/api/fhir/Patient?_id=BC9001234567" | jq .

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

# 4. Fetch Observations for Patient 1 (HbA1c & Blood Glucose)
curl http://localhost:8090/api/fhir/Observation/patient/1 | jq .

# 5. Fetch AllergyIntolerance for Patient 1 (Penicillin Allergy)
curl http://localhost:8090/api/fhir/AllergyIntolerance/patient/1 | jq .

# 6. Execute FHIR Bundle Transaction (Create Patient + Observation + Allergy)
curl -X POST http://localhost:8090/api/fhir \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "Bundle",
    "type": "transaction",
    "entry": [
      {
        "request": { "method": "POST", "url": "Patient" },
        "resource": {
          "resourceType": "Patient",
          "name": [{ "family": "Taylor", "given": ["Alex"] }],
          "gender": "other",
          "birthDate": "1995-08-20"
        }
      },
      {
        "request": { "method": "POST", "url": "Observation" },
        "resource": {
          "resourceType": "Observation",
          "status": "final",
          "code": { "coding": [{ "system": "http://loinc.org", "code": "8867-4", "display": "Heart Rate" }] },
          "valueQuantity": { "value": 78, "unit": "beats/min" }
        }
      }
    ]
  }' | jq .
```

### 8.2 Langley General Gateway (C# .NET 10 - Port 8083)

```bash
# 1. Ingest Raw HL7 v2 Message (ADT^A01 with OBX Lab Result) in .NET
curl -X POST http://localhost:8083/api/langleygeneral/hl7 \
  -H "Content-Type: text/plain" \
  -d $'MSH|^~\\&|SURREY_EHR|SURREY_MEM|REC_APP|REC_FAC|20260721100000||ADT^A01^ADT_A01|MSG99100|P|2.4\nPID|1||MRN-778899^^^MRN||Douglas^Liam||19821104|M|||789 King George Blvd^^Surrey^BC^V3T 2W1^CA||604-555-8811||||||9123456789\nPV1|1|I|2 East^204^B^Surrey Memorial||||Dr. Robert Vance|||||||||||VN-2026-99100|||||||||||||||||||||||||20260721100000\nOBX|1|NM|2339-0^Blood Glucose^LN||14.5|mmol/L|3.9-6.1|H|||F' | jq .

# 2. Fetch C# FHIR Patients
curl http://localhost:8083/fhir/Patient | jq .

# 3. Execute C# FHIR Bundle Transaction
curl -X POST http://localhost:8083/fhir \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "Bundle",
    "type": "transaction",
    "entry": [
      {
        "request": { "method": "POST", "url": "Patient" },
        "resource": {
          "resourceType": "Patient",
          "name": [{ "family": "Campbell", "given": ["Jordan"] }]
        }
      }
    ]
  }' | jq .
```

### 8.3 Langley Children's Hospital Backend (Java - Port 8081)

```bash
# 1. Sync Pediatric Allergy Webhook
curl -X POST http://localhost:8081/api/langley/pediatric/allergy-sync \
  -H "Content-Type: application/json" \
  -d '{
    "patientMrn": "MRN-10003",
    "allergyCode": "91930004",
    "allergyDisplay": "Allergy to Peanut",
    "category": "food",
    "criticality": "high",
    "reaction": "Anaphylaxis"
  }' | jq .

# 2. Fetch Synced Allergies for Patient 1
curl http://localhost:8081/api/patients/1/allergies | jq .
```

---

## 9. Regional Health Integration Architecture & System Landscape

When working in regional health authority ecosystems (such as British Columbia's regional and provincial health organizations), integration engineers build interfaces that connect hospital EHRs, provincial repositories, and specialty clinical systems:

```
 +-----------------------------------------------------------------------------------+
 |                             Provincial Ecosystem                                  |
 |                                                                                   |
 |  +--------------------+     +--------------------+     +-----------------------+  |
 |  |  Client Registry   |     |    PharmaNet       |     |  Provincial Lab (PLIS)|  |
 |  |    (EMPI / PHN)    |     | (Medication Recs)  |     |   (Lab Test Results)  |  |
 |  +---------+----------+     +---------+----------+     +-----------+-----------+  |
 +------------|--------------------------|----------------------------|--------------+
              | FHIR / HL7               | HL7 / Web Services         | HL7 v2 ORU
              v                          v                            v
 +-----------------------------------------------------------------------------------+
 |                       Regional Interface Engine (Mirth / Rhapsody)                |
 +-----------------------------------------------------------------------------------+
              |                          |                            |
              v                          v                            v
 +------------------------+  +------------------------+  +---------------------------+
 | Acute Care EHR (Cerner)|  | Community Gateway (C#) |  | Specialty Backend (Java)  |
 +------------------------+  +------------------------+  +---------------------------+
```

### 9.1 Enterprise System Components & Protocols

1. **Provincial Client Registry & EMPI (Enterprise Master Person Index):**
   - **Role:** Central source of truth for patient identity across the province.
   - **Key Standards:** Uses BC PHN (10 digits starting with `9`) as the primary key. Interfaced via FHIR `Patient/$match` or HL7 v2 `ADT^A40` (Merge Patient) and `QBP^Q22` (Query by Parameter).
2. **Acute Care Enterprise EHRs (e.g., CST Cerner, Meditech):**
   - **Role:** Main hospital clinical database for Inpatient ADT, Nursing Documentation, Physician Orders (CPOE), and Clinical Notes.
   - **Key Standards:** Emits continuous stream of HL7 v2 `ADT` (`A01` Admit, `A02` Transfer, `A03` Discharge, `A08` Update) and `ORU^R01` (Lab Results) over TCP/MLLP connections.
3. **PharmaNet (Provincial Drug Repository):**
   - **Role:** Central BC network connecting all community pharmacies and hospital emergency departments to maintain a unified provincial medication profile.
   - **Key Standards:** Interfaced via secure HL7 v3 / Web Services or FHIR `MedicationRequest` / `MedicationStatement`.
4. **PLIS (Provincial Laboratory Information System):**
   - **Role:** Central repository storing lab results from hospital labs and private community laboratories (e.g. LifeLabs).
   - **Key Standards:** Consumes and redistributes HL7 v2 `ORU^R01` messages with standardized LOINC codes.
5. **CareConnect (Provincial EHR Portal):**
   - **Role:** Clinician viewer aggregating acute, lab, imaging, and medication data into a unified longitudinal record.

### 9.2 Privacy & Security Compliance (FOIPPA / PII Masking)

Under British Columbia's **Freedom of Information and Protection of Privacy Act (FOIPPA)**:
- **PHI Masking in Logs:** System logs, interface engine traces, and exception dumps **MUST NEVER** output unmasked PHNs or patient names.
- **TLS Encryption:** All HTTP endpoints carrying FHIR JSON payloads must use TLS 1.3 in production environments (`https://`).
- **Audit Logging:** Every FHIR read/search operation must log the accessing user ID, patient target ID, and timestamp (auditable via FHIR `AuditEvent` resources).

---

## 10. Technical Architecture & System Design Q&A Reference

This section provides architectural scenarios, reference responses, and technical explanations designed for enterprise Java & C# health integration engineering.

---

### Q1: What is the difference between HL7 v2 ACKs (`AA`, `AE`, `AR`) and FHIR `OperationOutcome`? How do you handle transient vs permanent errors?

**Model Answer:**
- **HL7 v2 Acknowledgment (ACK):**
  - **`AA` (Application Accept):** Message parsed and processed successfully.
  - **`AE` (Application Error):** Business logic failure (e.g., patient not found, invalid PHN check digit). The sender **should NOT automatically retry** without payload correction.
  - **`AR` (Application Reject):** System-level or syntax failure (e.g., corrupted MSH segment, database down). The sender **MUST retry** using exponential backoff.
- **FHIR `OperationOutcome`:**
  - Standard FHIR resource returned with HTTP status codes (`400 Bad Request`, `404 Not Found`, `500 Internal Error`).
  - Contains `issue` elements with `severity` (`fatal`, `error`, `warning`, `information`), `code` (`invalid`, `not-found`, `transient`), and `diagnostics`.
- **Error Handling Strategy in Integration Engines:**
  - **Transient Errors (e.g. DB connection timeout, HTTP 503):** Route payload to a **Dead Letter Queue (DLQ)** with automatic retry policy (e.g., 3 retries at 10s, 60s, 300s).
  - **Permanent Errors (e.g. Invalid PHN Modulus-11 checksum, Malformed JSON):** Log masked payload to alert queue for manual interface analyst review.

---

### Q2: How do you guarantee **Idempotency** when ingesting high-volume HL7 v2 streams or processing FHIR POST requests?

**Model Answer:**
- **HL7 v2 Idempotency:**
  - Use `MSH-10` (**Message Control ID**) combined with `PID-3` (MRN) and event timestamp.
  - Maintain a deduplication table in PostgreSQL with a unique constraint on `(message_control_id, sending_facility)`.
  - If a duplicate message arrives within a defined window (e.g., 24 hours), return the stored `ACK` without re-executing database mutations.
- **FHIR REST Idempotency:**
  - Use **`PUT`** instead of `POST` when the client specifies the logical ID (`PUT /api/fhir/Patient/MRN-10001`).
  - For `POST` calls, implement conditional creates using the `If-None-Exist` header:
    `POST /api/fhir/Patient` with header `If-None-Exist: identifier=http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn|9001234567`.
  - In C# and Java, execute upsert queries (`ON CONFLICT (mrn) DO UPDATE`) to prevent primary key collisions.

---

### Q3: Explain the SMART on FHIR App Launch framework and how patient launch context is passed.

**Model Answer:**
1. **EHR Launch Sequence:**
   - The EHR opens an embedded iframe or webview targeting the SMART app's launch URL with `iss` (FHIR Server Base URL) and `launch` (opaque launch identifier).
2. **Authorization Request (`GET /oauth2/authorize`):**
   - The SMART app redirects to the FHIR Server's OAuth2 authorize endpoint, requesting scopes such as `launch/patient patient/*.read openid fhirUser`.
3. **Token Exchange (`POST /oauth2/token`):**
   - The app exchanges the received authorization code for an OAuth2 access token.
   - The token response includes the `access_token`, `expires_in`, and the **launch context parameter**:
     ```json
     {
       "access_token": "eyJhbGciOi...",
       "token_type": "Bearer",
       "scope": "launch/patient patient/*.read",
       "patient": "10001"
     }
     ```
4. **Context-Aware Querying:**
   - The SMART app uses the returned `"patient": "10001"` parameter to immediately query `/api/fhir/Observation?patient=10001` without prompting the user to re-select the patient.

---

### Q4: How do you handle patient identity matching conflicts (e.g., duplicate MRNs or missing PHNs)?

**Model Answer:**
- Implement a two-phase Client Registry matching architecture:
  1. **Deterministic Matching:**
     - Query exact matches on **BC PHN** (validated via Modulus-11). If matched, automatically link the record.
  2. **Probabilistic / Rule-Based Matching (CRS `$match`):**
     - Match on `(LastName + FirstName Soundex + DateOfBirth + Gender)`.
     - Assign match weight scores (e.g., Exact PHN = 100%, Name+DOB+Gender = 85%, Name+DOB only = 60%).
     - **Score $\ge$ 85%:** Auto-link.
     - **Score 50% - 84%:** Flag as potential duplicate for Health Records / HIM merge review (`ADT^A40`).
     - **Score < 50%:** Register as new patient.

---

### Q5: What is the difference between FHIR `transaction` and `batch` Bundles? How does error handling differ?

**Model Answer:**
- **FHIR `transaction` Bundle:**
  - Executed as an **atomic, single database transaction** (`@Transactional` in Spring Boot, `IDbContextTransaction` in EF Core).
  - If **ANY** entry in the bundle fails (e.g., 3rd entry out of 10 fails), the **entire bundle is rolled back**, no database changes persist, and a single `OperationOutcome` error is returned.
- **FHIR `batch` Bundle:**
  - Executed as **independent, un-bundled requests**.
  - Each entry is processed independently. If entry #3 fails, entries #1, #2, and #4-10 still succeed and persist.
  - The response bundle contains individual HTTP status codes (`201 Created`, `400 Bad Request`) for each entry.

---

### Q6: Why should you use `DateOnly` instead of `DateTime` for Patient Date of Birth in C# .NET healthcare applications?

**Model Answer:**
- A Date of Birth is a **calendar date** without time or timezone offset metadata.
- When `DateTime` (or PostgreSQL `timestamp with time zone`) is used:
  - DOB `1988-12-15T00:00:00Z` parsed on a server running in Pacific Time (UTC-8) shifts to `1988-12-14 16:00:00`.
  - This causes catastrophic clinical identity errors where patient birthdays change depending on server timezone configurations.
- In .NET 6+, using `DateOnly` maps natively to PostgreSQL `date` columns, eliminating timezone shifts completely and ensuring exact date preservation across distributed interfaces.

---

### Q7: How do you optimize Mirth Connect / Interface Engine throughput under high message volumes?

**Model Answer:**
1. **Asynchronous Destination Writers:** Set destination queues to `Asynchronous` mode so TCP receivers respond immediately with `ACK` without waiting for downstream HTTP/DB calls.
2. **Connection Pooling & Persistent Connections:** Reuse HTTP clients and database connection pools (`HikariCP` in Java, `Npgsql` pool in C#) rather than reopening socket connections per message.
3. **Selective Logging:** Disable full message XML/JSON content logging in production; log only Message IDs and MRNs.
4. **Batch DB Writes:** Aggregate inbound records into bulk inserts (`INSERT INTO ... VALUES (...)`) or FHIR Bundle transactions rather than executing individual single-row SQL statements.

---

### Q8: Compare Java (Spring Boot / HAPI FHIR) and C# (.NET 10 Web API) design patterns for building scalable healthcare integration services.

**Model Answer:**

| Architectural Concern | Java (Spring Boot) Implementation | C# (.NET 10 Web API) Implementation |
| :--- | :--- | :--- |
| **Dependency Injection** | `@Autowired` / `@RequiredArgsConstructor` (Lombok) | Built-in `IServiceCollection` (`AddScoped`, `AddSingleton`) |
| **ORM / Data Access** | Spring Data JPA / Hibernate (`JpaRepository`) | Entity Framework Core (`DbContext`, `DbSet<T>`) |
| **FHIR Schema Parsing** | HAPI FHIR Core (`ca.uhn.hapi.fhir`) / Jackson | System.Text.Json / Firely .NET SDK (`Hl7.Fhir.R4`) |
| **Async / Multi-threading** | CompletableFuture, Virtual Threads (Java 21) | `async` / `await`, Task Parallel Library (TPL) |
| **Transaction Management**| `@Transactional` annotation | `_context.Database.BeginTransactionAsync()` |
| **Date Modeling** | `java.time.LocalDate` | `System.DateOnly` |

---

## 11. US Core vs. Canadian Baseline (CA Baseline) FHIR Implementation Comparison

Healthcare integration engineers operating in North America must understand key differences between **US Core IG** (HL7 US Realm) and **Canadian Baseline IG (CA Baseline)** / BC Provincial Profiles:

| Architectural Dimension | US Core Implementation Guide (US Realm) | Canadian Baseline IG (CA Baseline / BC Profiles) |
| :--- | :--- | :--- |
| **Primary Patient Identifiers** | SSN (`.../us-ssn`), NPI (Providers), Driver's License | Provincial Health Number (e.g. BC PHN `.../ca-bc-patient-phn`), JHN (Jurisdictional Health Number), MRN |
| **Clinical Diagnoses & Coding** | ICD-10-CM, SNOMED CT | **ICD-10-CA** (Canadian modification of ICD-10), SNOMED CT |
| **Medical Procedures & Coding** | CPT (Current Procedural Terminology), ICD-10-PCS | **CCI** (Canadian Classification of Health Interventions) |
| **Medication Terminology** | RxNorm | **DIN** (Drug Identification Number / Health Canada DPD) & RxNorm / SNOMED CT |
| **Demographics & Ethnicity** | **OMB Race & Ethnicity** extensions (`us-core-race`, `us-core-ethnicity`) | Canadian Indigenous Status extensions, Official Language preference (`en-CA`, `fr-CA`) |
| **Geography & Address Formats** | US State (2-letter abbreviation), 5-digit ZIP code | Province/Territory (e.g. `BC`, `AB`), 6-character Alphanumeric Postal Code (`A1A 1A1`) |
| **Governance & Regulatory Standards** | US ONC (21st Century Cures Act mandates) | HL7 Canada / Canada Health Infoway & Provincial Digital Health Authorities |

### Key Technical Takeaways:

1. **System URIs & Patient Identifiers:**  
   US Core relies on SSN and NPI system URIs. Canadian interfaces use Canada Health Infoway naming systems (such as `http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn` for BC PHNs) and Modulus-11 check digit validation.
2. **Diagnosis & Procedure Code Sets:**  
   US systems use `ICD-10-CM` and `CPT`. Canadian enterprise implementations use **`ICD-10-CA`** for diagnoses and **`CCI`** (Canadian Classification of Health Interventions) for procedures.
3. **Medication Identifiers:**  
   In Canada, Health Canada assigns 8-digit **DINs** (Drug Identification Numbers) to licensed drug products. FHIR `MedicationRequest` resources in Canadian profile implementations map DINs alongside SNOMED CT / RxNorm.
4. **Demographic Extensions:**  
   US Core mandates US OMB Race/Ethnicity extensions (`us-core-race`). Canadian profiles omit OMB codes and utilize Canadian extensions for Indigenous identity and official language preference (`en-CA` / `fr-CA`).

---

## 12. Integration Engineering Competency Checklist

- [x] **Understand HL7 v2 vs FHIR R4 mapping:** PID → Patient, PV1 → Encounter, DG1 → Condition, OBX → Observation.
- [x] **Master BC PHN Validation:** Modulus-11 algorithm weights `[2, 4, 8, 5, 10, 9, 7, 3]`, starts with `9`, 10 digits total.
- [x] **Know US Core vs CA Baseline Differences:** ICD-10-CA vs ICD-10-CM, CCI vs CPT, DIN vs RxNorm, BC PHN vs SSN.
- [x] **Practice FHIR Bundle Transactions:** Test `POST /api/fhir` (Java) and `POST /fhir` (C#) using the included `curl` scripts.
- [x] **Know FOIPPA Privacy Controls:** PHI masking in logs (`912****789`), TLS 1.3 transport security, audit logging.
- [x] **Be ready for Architecture Questions:** Integration engines (Mirth/Rhapsody), EMPI client matching, SMART on FHIR OAuth2 flows.


