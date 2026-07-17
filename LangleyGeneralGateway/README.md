# Langley General Gateway (LangleyGeneralGateway)

A fictional gateway service representing **Langley General Hospital**. This is a modern **C# / .NET 10 Web API** microservice designed to receive, process, and persist patient demographic sync messages forwarded by a Mirth Connect integration pipeline.

This module provides hands-on demonstration of .NET enterprise backend engineering within a BC clinical integration context (aligned with PHSA and BC Public Service standards).

---

## 1. Core Technical Architecture & Design Decisions

When discussing this application in a technical interview (such as at PHSA), these are the key architectural decisions and patterns to highlight:

### 1.1 Semantic Date Representation (`DateOnly`)
* **The Choice:** Demographics models use the modern C# **`DateOnly`** type for `DateOfBirth` instead of the traditional `DateTime`.
* **The Rationale:** A date of birth is a calendar date only—it has no time or timezone context. In standard enterprise integrations, storing dates of birth as `DateTime` (or `timestamp with time zone` in PostgreSQL) frequently leads to **timezone shift bugs**. For example, a birthdate of `1988-12-15` transmitted from a different timezone can easily shift to `1988-12-14 23:00` or `1988-12-16 01:00`. `DateOnly` maps directly to the PostgreSQL `date` column type, fully eliminating this class of bugs.

### 1.2 Robust Defensive DTO Validation
* **The Challenge:** Default ASP.NET Core model validation can throw generic `400 Bad Request` exceptions directly from the System.Text.Json deserialization layer before the controller code is ever hit (resulting in unhelpful error details like `The JSON value could not be converted...`).
* **The Solution:** The `SyncPatientRequest` DTO models all properties as nullable strings (`string?`). This allows Mirth Connect to submit empty, null, or malformed values without breaking the pipeline. We then execute explicit, custom validation and parsing logic (e.g. `DateOnly.TryParse()`) in the Controller, returning clean, readable error responses.

### 1.3 Entity Framework Core (EF Core 10) & PostgreSQL Integration
* **Unique Constraints:** The `LangleyGeneralDbContext` maps the entities to a PostgreSQL schema. In the `OnModelCreating` configuration, a unique index constraint is defined on the patient **`Mrn`** (Medical Record Number) to guarantee data integrity across regional syncs.
* **Upsert (Sync) Pattern:** The sync controller uses an upsert design. If the MRN is already registered in the hospital registry, the service updates the demographic details; if the MRN is new, it automatically registers a new record.

---

## 2. Tech Stack

* **Runtime:** .NET 10.0
* **Framework:** ASP.NET Core Web API (Controller-based routing)
* **ORM:** Entity Framework Core (Code-First Migrations)
* **Database:** PostgreSQL (Database name: `langley_general_db` on port `5432`)
* **API Port:** `8083`

---

## 3. API Specs

### Sync Webhook Endpoint
* **Endpoint:** `POST /api/langleygeneral/sync`
* **Content-Type:** `application/json`
* **Payload:**
```json
{
  "mrn": "MRN-223388",
  "phn": "9123456789",
  "firstName": "Shane",
  "lastName": "Murphy",
  "dateOfBirth": "1988-12-15",
  "gender": "male"
}
```
* **Response (Created):**
```json
{
  "status": "success",
  "message": "Patient MRN-223388 created successfully."
}
```

### Roster Endpoint
* **Endpoint:** `GET /api/langleygeneral/patients`
* **Response:**
```json
[
  {
    "id": 3,
    "mrn": "MRN-223388",
    "phn": "9123456789",
    "firstName": "Shane",
    "lastName": "Murphy",
    "dateOfBirth": "1988-12-15",
    "gender": "male",
    "syncedAt": "2026-07-17T05:32:15.018566Z"
  }
]
```

---

## 4. How to Run Locally

1. Ensure your local PostgreSQL server is running.
2. Navigate to the project directory:
   ```bash
   cd LangleyGeneralGateway
   ```
3. Initialize/update the database schema:
   ```bash
   dotnet ef database update
   ```
4. Start the service:
   ```bash
   dotnet run
   ```
   *The gateway will compile and start listening on `http://localhost:8083`.*
