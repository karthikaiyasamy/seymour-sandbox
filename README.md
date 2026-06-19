# Healthcare Sandbox — Seymour Practice Server

A local FHIR R4-shaped REST API using Spring Boot + PostgreSQL.  
Synthetic BC patient data. Runs entirely offline. This is just an experimental project to brush up my HL7 skills and does not contain any real patient data. Please do not use this in production.

## Prerequisites
- Java 21+
- Maven 3.8+
- PostgreSQL (running locally on port 5432)

## Quick Start

```bash
cd seymour-sandbox
mvn clean spring-boot:run
```

Server starts at: **http://localhost:8090** (formerly 8080)

On first run, the database is auto-seeded with **4 synthetic patients**:
- Margaret Chen (MRN-10001) — T2DM, hyperglycemia, 7-day admission + transfer + discharge
- Robert Okafor (MRN-10002) — Anterior STEMI, post-PCI in CSICU
- Aisha Patel (MRN-10003) — Pediatric acute asthma exacerbation
- Fatima Al-Rashid (MRN-10004) — Prenatal 28-week outpatient visit

Databases:
- **Seymour Sandbox DB:** `seymour_db` (PostgreSQL)
- **Langley Hospital DB:** `langley_db` (PostgreSQL)

---

## API Endpoints

### Patients
| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/fhir/Patient` | All active patients |
| GET | `/api/fhir/Patient/{id}` | Patient by ID (returns `OperationOutcome` on 404) |
| GET | `/api/fhir/Patient?name=chen` | Search by name |
| GET | `/api/fhir/Patient?mrn=MRN-10001` | Lookup by MRN |
| POST | `/api/fhir/Patient` | Create patient (returns `OperationOutcome` if MRN exists) |
| PUT | `/api/fhir/Patient/{id}` | Update patient |
| DELETE | `/api/fhir/Patient/{id}` | Soft-deactivate patient |

### ADT Events (Admissions / Discharges / Transfers)
| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/fhir/Encounter/adt` | All ADT events |
| GET | `/api/fhir/Encounter/adt/patient/{patientId}` | ADT history for patient |
| GET | `/api/fhir/Encounter/adt/visit/{visitNumber}` | ADT timeline for visit |
| GET | `/api/fhir/Encounter/adt/{id}` | Single ADT event |
| GET | `/api/fhir/Encounter/adt/{id}/hl7` | Get raw pipe-delimited HL7 v2 representation |
| POST | `/api/fhir/Encounter/adt/{patientId}` | New ADT event |
| POST | `/api/fhir/Encounter/adt/hl7` | Ingest raw pipe-delimited HL7 v2 message (admit/register) |

### SMART on FHIR OAuth2 Simulation
| Method | URL | Description |
|--------|-----|-------------|
| GET | `/oauth2/authorize` | Authorize endpoint (redirects with auth code) |
| POST | `/oauth2/token` | Token exchange endpoint (form-urlencoded, returns access token + patient launch context) |

### Medications
| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/fhir/MedicationRequest/patient/{patientId}` | All meds for patient |
| GET | `/api/fhir/MedicationRequest/patient/{patientId}/active` | Active meds only |
| GET | `/api/fhir/MedicationRequest/visit/{visitNumber}` | Meds for visit |
| GET | `/api/fhir/MedicationRequest/{id}` | Single medication |
| POST | `/api/fhir/MedicationRequest/{patientId}` | Prescribe medication |
| PUT | `/api/fhir/MedicationRequest/{id}/status?status=STOPPED` | Update med status |

### Clinical Notes / Encounters
| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/fhir/DocumentReference/patient/{patientId}` | All notes for patient |
| GET | `/api/fhir/DocumentReference/visit/{visitNumber}` | Notes for visit |
| GET | `/api/fhir/DocumentReference/{id}` | Single note |
| POST | `/api/fhir/DocumentReference/{patientId}` | Create note |

---

## Example curl Commands

```bash
# Get all patients
curl http://localhost:8080/api/fhir/Patient | jq .

# Get ADT history for patient 1 (Margaret Chen)
curl http://localhost:8080/api/fhir/Encounter/adt/patient/1 | jq .

# Get raw HL7 v2 message representation of ADT event 1
curl http://localhost:8080/api/fhir/Encounter/adt/1/hl7

# Ingest raw HL7 v2 message to register and admit a patient
# Note: If the MRN does not exist, the system automatically registers the patient first
curl -X POST http://localhost:8080/api/fhir/Encounter/adt/hl7 \
  -H "Content-Type: text/plain" \
  -d $'MSH|^~\\&|SANDBOX_EHR|Surrey Memorial Hospital|REC_APP|REC_FAC|20241201093000||ADT^A01^ADT_A01|MSG99001|P|2.4\nPID|1||MRN-90001^^^MRN||Smith^John||19850515|M|||123 Broadway^^Vancouver^BC^V6T 1Z4^CA||604-555-9000||||||BC9009998888\nPV1|1|I|3 West^301^A^Surrey Memorial Hospital||||Dr. Arthur Pendelton|||||||||||VN-2024-99001|||||||||||||||||||||||||20241201093000\nDG1|1|I10|I21.09^ST elevation myocardial infarction^ICD-10|||A'

# Execute a SMART on FHIR OAuth2 Token request
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=simulated_auth_code_123&redirect_uri=http://localhost:3000/callback&client_id=my_clinical_app"
```

---

## Project Structure

```
seymour-sandbox/
├── pom.xml                                  ← Parent POM
├── SeymorFHIR/                              ← Seymour FHIR Server module
│   ├── pom.xml
│   ├── src/main/java/com/healthcare/sandbox/
│   │   ├── HealthcareSandboxApplication.java   ← Main entry point
│   │   ├── config/DataSeeder.java
│   │   ├── service/Hl7Service.java
│   │   ├── model/ (Patient.java, AdtEvent.java, Medication.java, Encounter.java)
│   │   └── controller/ (PatientController.java, AdtController.java, OAuth2Controller.java)
│   └── src/main/resources/
│       ├── static/admin/adt-console.html   ← Practice ADT Console
│       └── application.properties
├── LangleyChildrensHospital/
│   ├── langley-backend/                     ← Langley Backend module
│   │   ├── pom.xml
│   │   └── src/main/java/com/langley/hospital/
│   └── langley-frontend/                    ← Langley Frontend module
│       ├── package.json
│       ├── index.html
│       ├── main.js
│       └── style.css
```

---

## FHIR & Integration Standards Alignment

Resources are shaped like FHIR R4 and follow Canadian clinical baselines:
- **Patient** → Aligning with **CA Baseline (Canadian Baseline)**. Patient health card numbers use the official BC Personal Health Number (PHN) system URI: `http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn`.
- **ADT Events** → Mapped as FHIR `Encounter` resources. Includes structural `location` arrays linking to location resources.
- **HL7 v2 Message Exchange** → Fully supports parsing and generating pipe-delimited standard `MSH`, `PID`, `PV1`, and `DG1` segments.
- **SMART on FHIR** → Simulates the OAuth2 token grant handshake, returning access tokens accompanied by launch context patient IDs (e.g. `"patient": "1"`).
- **OperationOutcome Error Handling** → Failed requests (like duplicates or 404s) return standard FHIR `OperationOutcome` structures.

---

## Next Steps (practice exercises)
1. Add lab results (Observation resource)
2. Add allergy list as AllergyIntolerance resource
3. Connect to real external sandboxes (e.g., code.cerner.com)
4. Expand the OAuth2 simulation to validate token signatures (JWT)
5. Build an app to launch using the simulated SMART on FHIR OAuth2 flow

