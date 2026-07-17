# Langley General Gateway (LangleyGeneralGateway)

A gateway service representing the demographics registry for **Langley General Hospital**. This is a modern **C# / .NET 10 Web API** microservice designed to receive, process, and persist patient demographic sync messages forwarded by an integration engine pipeline.

This module handles automated patient registration, demographics updates, and database persistence in accordance with modern healthcare integration standards.

---

## 1. Technical Architecture & Design Decisions

The application employs several key architectural patterns to ensure reliability, data integrity, and compliance with healthcare data processing standards:

### 1.1 Timezone-Safe Date Processing (`DateOnly`)
* **Design Decision:** The domain entity uses the C# **`DateOnly`** type for `DateOfBirth` rather than the traditional `DateTime` object.
* **Technical Rationale:** A date of birth is a calendar date only—it does not carry time or timezone metadata. In distributed healthcare environments, transmitting dates of birth as `DateTime` (or storing them as `timestamp with time zone` in PostgreSQL) frequently leads to timezone shift errors. For example, a birthday transmitted as `1988-12-15T00:00:00` from an Eastern Time client can shift to `1988-12-14` or `1988-12-16` when parsed by a server configured to Pacific Time (or UTC). By using `DateOnly`, the API maps directly to the PostgreSQL `date` column type, eliminating timezone conversion errors.

### 1.2 Defensive DTO Validation & Input Normalization
* **Design Decision:** The `SyncPatientRequest` DTO models incoming demographic parameters as nullable strings (`string?`) and performs validation and type conversion explicitly in the controller layer.
* **Technical Rationale:** Standard ASP.NET Core model validation can reject payloads at the framework level (System.Text.Json deserialization) when type mismatches occur (such as Mirth sending empty strings `""` for date fields). By allowing loose binding at the DTO level and performing explicit parsing (`DateOnly.TryParse()`), the API prevents raw parsing exceptions, processes optional fields gracefully, and returns clean, structured error responses (`400 Bad Request`) to the client.

### 1.3 Entity Framework Core (EF Core 10) & PostgreSQL Integration
* **Data Integrity:** The `LangleyGeneralDbContext` configures a unique index constraint on the patient **`Mrn`** (Medical Record Number) to guarantee demographic consistency and prevent duplicate registrations.
* **Upsert (Sync) Pattern:** The sync controller uses an upsert design. If the MRN is already registered, the service updates the demographics; if the MRN is new, it automatically registers a new record.

---

## 2. Tech Stack

* **Runtime:** .NET 10.0
* **Framework:** ASP.NET Core Web API (Controller-based routing)
* **ORM:** Entity Framework Core (Code-First Migrations)
* **Database:** PostgreSQL (Database name: `langley_general_db` on port `5432`)
* **API Port:** `8083`

---

## 3. API Specifications

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
