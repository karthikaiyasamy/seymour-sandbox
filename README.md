# Healthcare Sandbox — Regional Interoperability & FHIR Workspace

A local FHIR R4-shaped REST API sandbox built with **Java (Spring Boot)** and **C# (.NET 10)**, designed to simulate real-world British Columbia (BC) clinical workflows and healthcare integration architectures.

**IMPORTANT: All patient data is synthetic and should not be used in any real-world clinical settings.**

---

## What This Demonstrates

This workspace showcases a fully functional regional health interoperability sandbox, demonstrating both Java and C# enterprise backend development in a multi-stack healthcare system.

### Key Integration Capabilities
* **Hybrid Technology Stack:** Integration between a Java Spring Boot clinical backend, a C# .NET 10 Web API registration gateway, a React/Vite front-end, Mirth Connect integration middleware, and PostgreSQL databases.
* **HL7 v2 → FHIR R4 Translation & Destination Multiplexing via Mirth Connect:** Automated transformation and routing of **ADT** (Admit, Discharge, Transfer), **ORU** (Observation Result / Lab Results), and **VXU** (Unsolicited Vaccination Record) messages to multiple independent backends.
* **BC-Standard Identity Validation (Modulus-11):** Implementation of the official British Columbia Personal Health Number (PHN) check digit validation algorithm in both Java and C# utility layers, verifying checksums and rejecting invalid cards.
* **PII Security & Masking:** Custom utility layers that mask Patient Health Information (PHI) in terminal and file logging (e.g. `9234567897` $\rightarrow$ `923****897`) to comply with BC FOIPPA data privacy regulations.
* **SMART on FHIR OAuth2 Simulation:** Secure app-launch authentication simulating authorization code grant flows, returning access tokens accompanied by launching patient context (`patient: "1"`).
* **Canadian Baseline FHIR Compliance:** Native support for CA Baseline patient profiles using BC PHNs as identifiers and BC-specific system URIs.
* **OperationOutcome Error Handling:** Implements robust error responses structured exactly as standard FHIR `OperationOutcome` resources.
* **Clinically Meaningful Synthetic Patients:** Pre-seeded with complex clinical scenarios (STEMI post-PCI, Type 2 diabetes with hyperglycemia, pediatric asthma exacerbation, and prenatal care).

---

## Prerequisites
- Java 21+
- Maven 3.8+
- .NET 10.0 SDK
- PostgreSQL (running locally on port 5432)

---

## Project Structure

```
seymour-sandbox/
├── pom.xml                                  ← Parent Maven POM
├── README.md                                ← Main Repository documentation
│
├── SeymorFHIR/                              ← Seymour EHR FHIR Server (Java / Spring Boot)
│   ├── pom.xml
│   ├── src/main/java/com/healthcare/sandbox/
│   │   ├── config/DataSeeder.java           ← Seeding complex patient records
│   │   ├── service/Hl7Service.java          ← HL7 v2 VXU/ORU serialization
│   │   ├── util/PhnValidator.java           ← BC PHN Modulus-11 check digit logic
│   │   └── controller/PatientController.java← Intercepts invalid PHNs, exposes FHIR endpoints
│   └── src/main/resources/static/admin/     ← Practice ADT Console
│
├── LangleyChildrensHospital/
│   ├── langley-backend/                     ← Pediatric Portal Backend (Java / Spring Boot)
│   │   └── src/main/java/com/langley/hospital/
│   │       ├── controller/WebhookController.java ← Ingests Mirth JSON payloads
│   │       └── util/PhnValidator.java       ← Inbound validation & masked logging
│   └── langley-frontend/                    ← Langley Clinician Dashboard (Vite / React)
│
└── LangleyGeneralGateway/                   ← Hospital Gateway (C# / .NET 10 Web API)
    ├── LangleyGeneralGateway.csproj         ← EF Core & Npgsql PostgreSQL configuration
    ├── Program.cs                           ← Services registration & middleware routing
    ├── Utils/PhnValidator.cs                ← C# Modulus-11 validation & PII masking
    ├── Controllers/SyncController.cs        ← Safe DTO binding & manual validation upserts
    └── Data/LangleyGeneralDbContext.cs      ← DB context, MRN unique index configuration
```

---

## Quick Start

### 1. Run the Seymour EHR Server (Java)
```bash
cd SeymorFHIR
mvn clean spring-boot:run
```
*Seymour EHR starts at: **http://localhost:8090** (DB: `seymour_db`)*

### 2. Run the Langley Pediatric Backend (Java)
```bash
cd LangleyChildrensHospital/langley-backend
mvn clean spring-boot:run
```
*Langley Children's backend starts at: **http://localhost:8081** (DB: `langley_db`)*

### 3. Run the Langley General Gateway (C#)
```bash
cd LangleyGeneralGateway
dotnet ef database update       # Apply EF Core Migrations
dotnet run
```
*Langley General Gateway starts at: **http://localhost:8083** (DB: `langley_general_db`)*

### 4. Run the Langley Pediatric Frontend (Vite/React)
```bash
cd LangleyChildrensHospital/langley-frontend
npm install
npm run dev
```
*Frontend interface starts at: **http://localhost:8082***

---

## Mirth Connect Integration Architecture

The regional integration architecture is mediated by **Mirth Connect** (open source integration engine) to simulate real-world interface connectivity. 

```
                       ┌─────────────────────────┐
                       │    Seymour EHR (Java)   │
                       └────────────┬────────────┘
                                    │
                         HL7 v2 (VXU / ORU) over TCP
                                    │
                                    ▼
                       ┌─────────────────────────┐
                       │   Mirth Connect Port    │
                       │          9085           │
                       └──────┬───────────┬──────┘
                              │           │
           JSON over HTTP POST│           │JSON over HTTP POST
            (Port 8081 /sync) │           │ (Port 8083 /sync)
                              ▼           ▼
  ┌─────────────────────────────┐       ┌─────────────────────────────┐
  │ Langley Children's (Spring) │       │ Langley General C# Gateway  │
  │     Pediatric Clinical      │       │     Demographic Registry    │
  └─────────────────────────────┘       └─────────────────────────────┘
```

1. **Source Interface:** Mirth Connect hosts a TCP Listener (MLLP) on port `9085`.
2. **Global Source Transformer:** 
   * Extracts clinical payloads (`dataType`, `vaccineCode`, `lotNumber`, `resultValue`).
   * Safely extracts and normalizes patient demographics (`patientMrn`, `firstName`, `lastName`, `dateOfBirth`, `genderMapped`, `patientPhn`).
   * Writes variables to both `channelMap` and `connectorMap` to ensure they are visible across destination templates.
3. **Destination 1 (Langley Children's Webhook):** Dispatches JSON payloads to `http://localhost:8081/api/langley/pediatric/sync`.
4. **Destination 2 (Langley General Gateway):** Dispatches demographic sync payloads to `http://localhost:8083/api/langleygeneral/sync`.

---

## API Endpoints & Verification

### C# General Gateway Endpoint Specs
* **Endpoint:** `POST /api/langleygeneral/sync`
* **JSON Request Body:**
```json
{
  "phn": "9234567897",
  "mrn": "LGH-123456",
  "firstName": "John",
  "lastName": "Doe",
  "dateOfBirth": "1990-01-01",
  "gender": "Male"
}
```
* **Validation Outcome (Valid PHN):** Returns `200 OK` or `201 Created`. Masks logs securely:
  `info: LangleyGeneralGateway.Controllers.SyncController: Processing demographics sync for MRN: LGH-123456, PHN: 923****897`
* **Validation Outcome (Invalid Checksum):** Returns `400 Bad Request`.
  ```json
  {
    "status": "error",
    "message": "Invalid British Columbia PHN format or checksum."
  }
  ```
  `warn: LangleyGeneralGateway.Controllers.SyncController: Demographics sync rejected: Invalid PHN format/checksum '923****893'`
