# 🎬 Seymour Sandbox - 5-Minute Technical Interview Demo Script

This script provides an exact, step-by-step 5-minute technical demo script designed for **PHSA**, **Fraser Health**, and enterprise healthcare architecture interviews.

---

## ⏱️ Timeline Overview

| Time | Module | Key Showcase Topic |
| :--- | :--- | :--- |
| **0:00 - 1:00** | **System Architecture & Federation** | Multi-hospital topology, SMART-on-FHIR OAuth2 security, and C# + Java dual-stack. |
| **1:00 - 2:30** | **HL7 v2 Audit & Idempotency** | Processing ADT^A01 feeds, MSH-10 message tracking, and duplicate rejection. |
| **2:30 - 3:30** | **Patient Identity Conflict Resolution** | Match score calculation, BC Modulus-11 PHN validation, and `PENDING_REVIEW` queue. |
| **3:30 - 4:30** | **Cross-Hospital Real-Time Sync** | Webhook notifications, FHIR R4 Patient/Observation APIs, and Terry Fox cancer node sync. |
| **4:30 - 5:00** | **Resilience & Governance** | Resilience4j Semaphore Bulkhead heavy report fallback and GitHub Actions CI/CD. |

---

## 🛠️ Step-by-Step Demo Execution

### Step 1: System Topology & Launch (0:00 - 1:00)
1. Show the single-command launch in terminal:
   ```bash
   docker-compose up --build
   ```
2. Explain the 4 containerized nodes:
   - **Seymour Central EHR (Spring Boot, Port 8090):** Main clinical repository & OAuth2 provider.
   - **Langley General Gateway (.NET 10 / C#, Port 8083):** Regional hospital gateway with EF Core auto-schema.
   - **Terry Fox Memorial (HAPI FHIR R4, Port 8085):** Oncology mCODE staging engine.
   - **Langley Children's Backend (Spring Boot, Port 8081):** Pediatric specialty node.

---

### Step 2: HL7 v2 Processing & MSH-10 Idempotency (1:00 - 2:30)
1. **Request Bearer Authorization Token:**
   ```bash
   curl -X POST http://localhost:8090/oauth/token \
     -d "grant_type=authorization_code&code=SMART_AUTH_SYNC&client_id=seymour_smart_app"
   ```
2. **Trigger ADT^A01 Admission Feed via Endpoint:**
   ```bash
   curl -X POST http://localhost:8090/api/fhir/Encounter/adt/hl7 \
     -H "Authorization: Bearer <access_token>" \
     -H "Content-Type: text/plain" \
     -d "MSH|^~\&|VGH|VANCOUVER_GENERAL|SEYMOUR|CENTRAL|20260806190000||ADT^A01^ADT_A01|MSG-900881|P|2.4
   PID|1||MRN-901881^^^MRN||Smith^Alexander||19880412|M|||123 Main St^^Vancouver^BC^V5K 1A1^CA||604-555-0199||||||9000000071
   PV1|1|I|3 East^302^B||||Dr. Sarah Park|||||||||||VN-881"
   ```
3. **Demonstrate MSH-10 Idempotency Protection:**
   Re-send the *exact same payload* immediately.
   - **Expected Outcome:** Rejection error (`Duplicate MSH-10 Message Control ID rejected: MSG-900881`).
   - **Talking Point:** *"In real hospital interfaces, network retries happen frequently. Storing MSH-10 control IDs and SHA-256 payload hashes prevents duplicate patient creation."*

---

### Step 3: Patient Identity Conflict Resolution (2:30 - 3:30)
1. **Simulate Ambiguous Patient Match:**
   Send an inbound HL7 message with a different inbound MRN (`MRN-CONFLICT-99`) but matching name (`Smith^Alex`) and different DOB:
   ```bash
   curl -X POST http://localhost:8090/api/fhir/Encounter/adt/hl7 \
     -H "Authorization: Bearer <access_token>" \
     -H "Content-Type: text/plain" \
     -d "MSH|^~\&|VGH|VANCOUVER_GENERAL|SEYMOUR|CENTRAL|20260806190500||ADT^A08^ADT_A08|MSG-CONF-01|P|2.4
   PID|1||MRN-CONFLICT-99^^^MRN||Smith^Alex||19951020|M|||123 Main St^^Vancouver^BC^V5K 1A1^CA||604-555-0199||||||9000000071
   PV1|1|O|Outpatient Clinic"
   ```
   - **Expected Outcome:** Patient creation halted (`HL7 Identity Conflict Detected (Match Score: 0.6). Patient creation halted. Record queued for manual PENDING_REVIEW.`).
2. **Inspect & Resolve Conflict Queue via API:**
   Query the pending review queue:
   ```bash
   curl -X GET http://localhost:8090/api/fhir/Patient/match-reviews \
     -H "Authorization: Bearer <access_token>"
   ```
   Approve the record manually:
   ```bash
   curl -X POST http://localhost:8090/api/fhir/Patient/match-reviews/1/approve \
     -H "Authorization: Bearer <access_token>"
   ```

---

### Step 4: SMART on FHIR OAuth2 & Cross-Hospital Sync (3:30 - 4:30)
1. **Request OAuth Token:**
   ```bash
   curl -X POST http://localhost:8090/oauth/token \
     -d "grant_type=authorization_code&code=SMART_AUTH_SYNC&client_id=seymour_smart_app"
   ```
2. **Query FHIR Patient Resource with Bearer Token:**
   ```bash
   curl -X GET http://localhost:8090/api/fhir/Patient/1 \
     -H "Authorization: Bearer <access_token>"
   ```

---

### Step 5: Resilience & CI/CD Pipeline (4:30 - 5:00)
1. **Show Resilience4j Bulkhead Fallback:**
   Trigger high-concurrency export query `GET /api/reports/heavy-export/1`. Show standard FHIR `OperationOutcome` with `HTTP 429 Too Many Requests`.
2. **Show GitHub Actions Pipeline:**
   Highlight `.github/workflows/ci.yml` running Java 21 & .NET 10 builds with 30 passing unit tests.

---

## 🏆 Senior Engineering Discussion Points

- **Why Dual-Stack (Java + C#)?** Demonstrates flexibility across traditional enterprise Microsoft environments (Langley Gateway) and Java Spring Boot / HAPI FHIR ecosystems (Seymour / Terry Fox).
- **FOIPPA Compliance:** Terminal and log statements mask PHNs and MRNs while returning tracing correlation IDs.
- **Atomic Transactions:** FHIR Bundle `transaction` mode uses Spring `TransactionAspectSupport.setRollbackOnly()` on entry failures to guarantee zero partial commits.
