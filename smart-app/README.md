# Seymour SMART-on-FHIR Clinical Portal (Angular 17 SPA)

`smart-app` is a modern Single-Page Application (SPA) built with standalone Angular 17. It serves as a federated clinical dashboard providing patient directory searching, SMART-on-FHIR v2.0 authorization, cross-node patient discovery, an Enterprise Master Patient Index (EMPI) reconciliation engine, and a live emergency RSA key rotation simulator.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Single-File Component Architecture (`main.ts`)](#2-single-file-component-architecture-maints)
3. [Search-Driven Clinical Workflow & Directory Search](#3-search-driven-clinical-workflow--directory-search)
4. [Just-In-Time (JIT) OAuth Authentication Handshake](#4-just-in-time-jit-oauth-authentication-handshake)
5. [Cross-Node Federated Search Algorithm](#5-cross-node-federated-search-algorithm)
6. [Enterprise Master Patient Index (EMPI) Reconciliation Engine](#6-enterprise-master-patient-index-empi-reconciliation-engine)
7. [Live Emergency RSA Key Rotation Simulator](#7-live-emergency-rsa-key-rotation-simulator)
8. [Local Execution & Build Instructions](#8-local-execution--build-instructions)

---

## 1. Architecture Overview

`smart-app` acts as the primary web application for clinicians accessing regional EHR data across multiple hospital nodes.

```
[ Angular 17 Clinical Portal (Port 4200) ]
                   │
                   ├─── Search Directory (PHN / MRN / Name)
                   │
                   ├─── Query 1: Seymour Regional EHR (Port 8090)
                   │    - Loads LOINC Vitals & Demographics
                   │
                   ├─── Query 2 (Fallback): Terry Fox Hospital (Port 8085)
                   │    - Loads mCODE Cancer Staging & Genomics
                   │
                   └─── EMPI Engine: Computes Match Confidence Score
```

---

## 2. Single-File Component Architecture (`main.ts`)

### Design & Architectural Note
The application is constructed using Angular 17's standalone component model in `main.ts`. Near the top of the file, an explicit inline comment outlines the architectural pattern:

```typescript
// Single-file standalone component — intentional SFC pattern (Angular 17+).
// In a production multi-page portal with complex routing, this would be split into feature modules.
```

### Component State Properties (`AppComponent`)
- `token: string | null`: Active RS256 Bearer JWT token issued by Seymour Auth Server.
- `patient: any`: Current patient demographic object.
- `observations: any[]`: Array of LOINC-coded vital signs and lab results.
- `terryFoxData: any`: Oncology data payload retrieved from Terry Fox Hospital.
- `empiAnalysis: any`: Demographic match confidence analysis object.
- `flaggedForAudit: boolean`: Flag indicating whether record has been queued for EMPI audit review.
- `keyRotationNotice: any`: Data payload from key rotation execution.

---

## 3. Search-Driven Clinical Workflow & Directory Search

No patient data is loaded on initial page access. Clinicians interact with a Search Directory bar accepting PHN (`BC9001234567`, `9234567897`), MRN (`MRN-10001`), or Patient Name (`Margaret Chen`).

Quick Selection pills provide instant testing shortcuts for preset cohorts.

---

## 4. Just-In-Time (JIT) OAuth Authentication Handshake

If a user executes a search when unauthenticated (`this.token == null`), `searchPatient()` invokes `launchSmartAuthAndSearch(cleanQuery)`:

1. Executes HTTP GET `http://localhost:8090/.well-known/smart-configuration`.
2. Reads token endpoint URL (`http://localhost:8090/oauth/token`).
3. Executes HTTP POST to `/oauth/token` with grant type `authorization_code` and client ID `seymour_smart_app`.
4. Saves returned RS256 Bearer access token into `this.token`.
5. Automatically resumes patient search execution.

---

## 5. Cross-Node Federated Search Algorithm

When `executeSearch(cleanQuery)` runs:

1. Sends HTTP GET `http://localhost:8090/api/fhir/Patient?name=<query>` with `Authorization: Bearer <token>`.
2. If patient is found in Seymour EHR: Loads demographics and fetches LOINC vitals via `/api/fhir/Observation/patient/<id>`.
3. Concurrently queries Terry Fox Cancer Hospital (`http://localhost:8085/fhir/Patient?identifier=<phn/mrn>`).
4. If Seymour EHR returns no matches, automatically falls back to Terry Fox Hospital, displaying Sarah Jenkins' oncology profile (`9234567897`).

---

## 6. Enterprise Master Patient Index (EMPI) Reconciliation Engine

When cross-hospital records are retrieved (e.g. Margaret Chen in Seymour vs Margaret Chen in Terry Fox), `runEmpiReconciliation()` evaluates field deltas:

1. **Evaluated Fields:** Date of Birth (`1948-03-15` vs `1948-03-12`), Given Name (`Margaret` vs `Margaret A.`), PHN (`BC9001234567`).
2. **Match Score Calculation:** Computes match confidence percentage (e.g. `83%`).
3. **UI Warning Banner:** Displays an amber EMPI Identity Conflict Warning Banner listing specific field discrepancies.
4. **Audit Review Action:** Renders a **"Flag Record for EMPI Audit Review"** button, setting `flaggedForAudit = true` to simulate HIM review queuing.

---

## 7. Live Emergency RSA Key Rotation Simulator

Clicking **"Rotate RSA Keys Live"** triggers `rotateRsaKeys()`:

1. Sends HTTP POST `http://localhost:8090/api/admin/rotate-keys`.
2. Seymour Auth Server generates a new 2048-bit RSA keypair in PostgreSQL and updates `/.well-known/jwks.json`.
3. Re-authenticates token and queries Terry Fox Hospital.
4. Terry Fox detects unknown key ID, evicts RAM cache, re-fetches JWKS, and verifies request with zero downtime.

---

## 8. Local Execution & Build Instructions

```bash
# Install NPM dependencies
npm install

# Run Angular development server
npm start
```

Navigate browser to `http://localhost:4200`.
