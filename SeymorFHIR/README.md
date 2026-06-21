# Seymour FHIR Server (SeymorFHIR)

The Seymour FHIR Server acts as the primary simulated Electronic Health Record (EHR) database and FHIR service provider for the sandbox. It simulates clinical workflows, registers patients, records visits/encounters, and handles secure application authorization.

## Core Roles
* **EHR Provider:** Serves as the central repository for patient demographics, ADT history, and clinical documentation.
* **FHIR R4 API Endpoint:** Exposes endpoints modeled after official FHIR R4 resources to allow third-party client apps to request patient data securely.
* **HL7 Ingestion Engine:** Parses incoming pipe-delimited HL7 v2 messages and converts them into structured relational database entities.
* **Identity Provider (IdP):** Simulates the SMART on FHIR authorization code handshake, allowing secure OAuth2 token issuance.

## Tech Stack
* **Java 21** & **Spring Boot**
* **Spring Data JPA** & **Hibernate**
* **PostgreSQL** (Database name: `seymour_db`)
* **Thymeleaf / Static HTML** (For the administrative ADT console)

## Key Endpoints Exposed

### 1. FHIR Resources
* `/api/fhir/Patient` - Query, create, update, and search patients.
* `/api/fhir/Encounter/adt` - Query admission, transfer, and discharge events.
* `/api/fhir/MedicationRequest` - Manage patient prescriptions.
* `/api/fhir/DocumentReference` - Clinical notes and summaries.

### 2. SMART on FHIR OAuth2
* `GET /oauth2/authorize` - Simulates the OAuth2 login redirect.
* `POST /oauth2/token` - Simulates token exchange, returning an access token alongside active patient context (e.g. `patient: "1"`).

### 3. ADT Console UI
* Access via `http://localhost:8090/admin/adt-console.html` when running. Allows manually triggering simulated HL7 v2 ADT, ORU, or VXU messages.
