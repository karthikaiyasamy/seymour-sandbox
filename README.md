# Enterprise Regional Healthcare Interoperability & Federated Security Sandbox

A comprehensive, production-grade regional healthcare sandbox demonstrating federated clinical data exchange, **SMART-on-FHIR v2.0 authorization**, **zero-downtime RSA key rotation**, **JWKS public key verification**, **Enterprise Master Patient Index (EMPI) identity reconciliation**, and **mCODE oncology data models** across **Java 21 (Spring Boot & HAPI FHIR R4)**, **C# (.NET 10)**, and **Angular 17 SPA**.

> ⚠️ **IMPORTANT EDUCATIONAL & SYNTHETIC DATA DISCLAIMER**:  
> **This repository is an open-source engineering reference implementation designed for learning enterprise healthcare architecture, HL7 v2 messaging, FHIR R4 specifications, and regional identity governance. All patient records, Personal Health Numbers (PHNs), Medical Record Numbers (MRNs), diagnoses, lab results, and clinical trial datasets are 100% synthetic, fictitious, and artificially generated. No real Personal Health Information (PHI) is used or stored.**

> 📘 **Architectural Masterclass Documentation:**  
> Read the complete architectural deep-dives:
> - **[FHIR & Healthcare Integration Engineering Masterclass](FHIR_AND_HEALTHCARE_INTEGRATION_GUIDE.md)**
> - **[Regional Health Federated Architecture Overview](docs/ARCHITECTURE_OVERVIEW.md)**

---

## 🏛️ System Architecture Overview

The sandbox simulates a regional health authority ecosystem composed of four microservices and a clinical frontend:

```
                               ┌─────────────────────────────────────────────────────────┐
                               │                Seymour Regional EHR                     │
                               │          (Java 21 / Spring Boot 3.2.5)                  │
                               │  Port: 8090 | DB: seymour_db (PostgreSQL / Flyway)      │
                               │  - Persistent RSA Key Store (`oauth_keys`)              │
                               │  - Live Key Rotation (`/api/admin/rotate-keys`)         │
                               │  - JWKS Endpoint (`/.well-known/jwks.json`)             │
                               └──────────────────────────┬──────────────────────────────┘
                                                          │
                                                          │ Cross-Hospital JWKS & Bearer JWT Verification
                                                          ▼
┌─────────────────────────────────────────┐   ┌─────────────────────────────────────────┐
│     ANGULAR SMART CLINICAL PORTAL       │   │    TERRY FOX CANCER HOSPITAL NODE       │
│        (Angular 17 / Port 4200 / SPA)   │   │    (Java 21 / HAPI FHIR R4 Engine)      │
│  - Search Directory & Quick Selection   │   │  Port: 8085                             │
│  - Cross-Node Federated Patient Search  │──▶│  - Native HAPI RestfulServer            │
│  - EMPI Identity Reconciliation Engine  │   │  - `kid`-Driven JWKS Cache Eviction     │
│  - Emergency Key Rotation Trigger Button│   │  - mCODE Oncology & Genomics Suite      │
└─────────────────────────────────────────┘   └─────────────────────────────────────────┘
                                                          │
                                                          ▼
┌─────────────────────────────────────────┐   ┌─────────────────────────────────────────┐
│        LANGLEY GENERAL GATEWAY          │   │   LANGLEY CHILDREN'S HOSPITAL BACKEND   │
│         (C# / .NET 10 Web API)          │   │         (Java / Spring Boot)            │
│  Port: 8083                             │   │  Port: 8081                             │
│  - C# FHIR R4 Controllers & Bundles     │   │  - Pediatric Sync Webhooks              │
│  - Raw HL7 v2 Segment Ingestion         │   │  - Modulus-11 PHN Checksum Logic        │
└─────────────────────────────────────────┘   └─────────────────────────────────────────┘
```

---

## 🔥 Key Technical & Architectural Features

### 1. Persistent RSA Key Store & Zero-Downtime Key Rotation
- **PostgreSQL Key Store (`oauth_keys`):** RSA keypairs are serialized via PKCS#8 / X.509 PEM standards and stored in PostgreSQL, ensuring active signing keys survive server restarts.
- **On-Demand Key Rotation (`POST /api/admin/rotate-keys`):** Seymour Auth Server generates fresh 2048-bit RSA keypairs on demand, updating its active signing key and publishing updated public keys via `/.well-known/jwks.json`.
- **Dynamic `kid` Cache Eviction:** Terry Fox HAPI FHIR Node inspects incoming JWT headers for `kid` (Key ID). Encountering an unmapped key ID automatically triggers a cache eviction and re-fetch from Seymour's JWKS endpoint with zero downtime.

### 2. Cross-Hospital Federated Authorization & JWKS
- **Stateless Bearer JWT Verification:** Remote resource servers verify RS256 Bearer tokens statelessly using public keys fetched from Seymour's JWKS endpoint.
- **HAPI FHIR Security Interceptor:** Implements HAPI's `@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)` void hook to enforce Bearer token validation and CORS pre-flight bypassing.

### 3. Enterprise Master Patient Index (EMPI) Reconciliation
- **Demographic Discrepancy Detection:** When cross-hospital records share a unique PHN/MRN identifier but contain minor demographic deltas (e.g. 3-day Date of Birth discrepancy), the system calculates a match confidence score.
- **Interactive Discrepancy Flagging:** The UI highlights specific demographic conflicts and provides a **"Flag for EMPI Audit Review"** action to queue records for administrative review.

### 4. Search-Driven Clinical Portal & Cross-Node Federated Search
- **Search-First Workflow:** No patient record is auto-populated on page load. Clinicians enter a PHN, MRN, or Name into the Search Directory.
- **Federated Fallback Search:** Searches primary EHR node first (`Seymour EHR`), automatically falling back to regional specialty nodes (`Terry Fox Cancer Hospital`) if unmapped.

### 5. Standard-Compliant FHIR R4 & mCODE Oncology
- **Resource Suite:** Implements `Patient`, `Encounter`, `Observation` (LOINC-coded vitals/labs), `AllergyIntolerance` (SNOMED-coded allergies), `MedicationRequest`, `Condition` (mCODE TNM Staging), `ResearchStudy`, `ResearchSubject`, and `DiagnosticReport` (Genomic Biomarkers).
- **Atomic FHIR Bundles:** Transaction and batch processing enforcing database rollbacks on entry failures.

---

## 🛠️ Microservice Directory & API Summary

| Module | Stack | Port | Primary Responsibilities |
| :--- | :--- | :--- | :--- |
| **`SeymorFHIR`** | Java 21 / Spring Boot 3.2 | `8090` | Primary EHR Node, Custom FHIR R4 APIs, PostgreSQL Key Store (`oauth_keys`), Live Key Rotation (`/api/admin/rotate-keys`), SMART OAuth (`/oauth/token`), JWKS (`/.well-known/jwks.json`). |
| **`TerryFoxMemorial`** | Java 21 / HAPI FHIR R4 | `8085` | Regional Specialty Oncology Node, Native HAPI `RestfulServer`, `kid`-Driven JWKS Cache Eviction, mCODE Cancer Staging & Genomics. |
| **`smart-app`** | Angular 17 / TypeScript | `4200` | Search-Driven Clinical Portal, SMART OAuth Handshake, EMPI Reconciliation Engine, Live Key Rotation Simulator. |
| **`LangleyGeneralGateway`** | C# / .NET 10 | `8083` | Dual-Stack Gateway, C# FHIR R4 APIs & Bundles, Raw HL7 v2 Segment Ingestion. |
| **`langley-backend`** | Java 21 / Spring Boot | `8081` | Pediatric Portal Backend, Inbound Sync Webhooks, Modulus-11 PHN Checksum Logic. |

---

## 🚀 Quick Start & Local Deployment

### Option A: Docker Compose (Recommended)
Spin up PostgreSQL, Seymour EHR, Terry Fox HAPI Server, Langley Gateway, and the Angular Portal in a single command:

```bash
docker-compose up --build
```

Access Points:
- **Angular SMART Portal:** `http://localhost:4200`
- **Seymour EHR Swagger UI:** `http://localhost:8090/swagger-ui.html`
- **Terry Fox HAPI Capability Statement:** `http://localhost:8085/fhir/metadata`

---

### Option B: Local Terminal Launch

#### 1. Build Java Microservices
```bash
mvn clean package -DskipTests
```

#### 2. Start Seymour Regional EHR (Port 8090)
```bash
cd SeymorFHIR
mvn spring-boot:run
```

#### 3. Start Terry Fox HAPI FHIR Server (Port 8085)
```bash
cd TerryFoxMemorial
mvn spring-boot:run
```

#### 4. Start Angular SMART Portal (Port 4200)
```bash
cd smart-app
npm start
```

---

## 📂 Repository Layout

```
seymour-sandbox/
├── pom.xml                                     ← Root Maven Multi-Module POM
├── README.md                                   ← Master Repository Documentation
├── FHIR_AND_HEALTHCARE_INTEGRATION_GUIDE.md    ← Deep-Dive Architectural Masterclass
├── docs/
│   └── ARCHITECTURE_OVERVIEW.md                ← High-Level System Architecture Guide
│
├── SeymorFHIR/                                 ← Seymour EHR (Java / Spring Boot)
│   ├── src/main/resources/db/migration/        ← Flyway Database Migrations (V1, V2)
│   └── src/main/java/com/healthcare/sandbox/
│       ├── controller/AdminKeyRotationController.java ← Live RSA Key Rotation
│       └── service/JwtKeyService.java          ← PostgreSQL Key Store & PEM Serializer
│
├── TerryFoxMemorial/                           ← Terry Fox Cancer Node (HAPI FHIR R4)
│   └── src/main/java/com/terryfox/hospital/
│       ├── config/TerryFoxHapiServerConfig.java← HAPI RestfulServer & CorsInterceptor
│       ├── interceptor/TerryFoxSecurityInterceptor.java ← HAPI Void Pre-Handled Hook
│       └── service/TerryFoxJwksKeyService.java ← Dynamic kid Cache Eviction
│
├── smart-app/                                  ← Clinical Frontend (Angular 17)
│   └── src/main.ts                             ← Search Directory, EMPI Engine, Key Rotation UI
│
├── LangleyGeneralGateway/                      ← Gateway Service (C# / .NET 10)
└── LangleyChildrensHospital/                   ← Pediatric Backend (Java / Spring Boot)
```
