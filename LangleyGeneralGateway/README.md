# Langley General Gateway (C# .NET 10 Web API)

A dual-stack gateway service representing the demographic and registration registry for Langley General Hospital. Built on C# and .NET 10 Web API, it receives, validates, and persists patient demographic sync messages and HL7 v2 payloads.

---

## Table of Contents

1. [Architectural Overview](#1-architectural-overview)
2. [Timezone-Safe Date Processing (`DateOnly`)](#2-timezone-safe-date-processing-dateonly)
3. [Defensive DTO Binding & Parsing (`SyncPatientRequest.cs`)](#3-defensive-dto-binding--parsing-syncpatientrequestcs)
4. [BC PHN Modulus-11 Validation (`PhnValidator.cs`)](#4-bc-phn-modulus-11-validation-phnvalidatorcs)
5. [C# HL7 v2 Segment Parser (`Hl7Parser.cs`)](#5-c-hl7-v2-segment-parser-hl7parsercs)
6. [EF Core 10 PostgreSQL Persistence (`LangleyGeneralDbContext.cs`)](#6-ef-core-10-postgresql-persistence-langleygeneraldbcontextcs)
7. [API Endpoint Reference](#7-api-endpoint-reference)
8. [Local Execution Instructions](#8-local-execution-instructions)

---

## 1. Architectural Overview

Langley General Gateway serves as the C# .NET 10 endpoint in the regional health authority sandbox, demonstrating dual-stack interoperability between Java and .NET microservices.

---

## 2. Timezone-Safe Date Processing (`DateOnly`)

### Technical Rationale
The patient domain entity uses the C# `DateOnly` type for `DateOfBirth` rather than `DateTime`.
A birth date is a calendar date without time or timezone offset. Transmitting birth dates as `DateTime` (or storing as `timestamp with time zone` in PostgreSQL) causes timezone shift errors when parsed across Eastern, UTC, and Pacific servers. `DateOnly` maps directly to PostgreSQL's `date` column type, eliminating timezone conversion errors.

---

## 3. Defensive DTO Binding & Parsing (`SyncPatientRequest.cs`)

The `SyncPatientRequest` DTO models incoming demographic parameters as nullable strings (`string?`) and performs explicit parsing (`DateOnly.TryParse()`) inside `SyncController.cs`. This prevents framework-level deserialization exceptions when Mirth Connect or external interfaces send empty string representations for date fields.

---

## 4. BC PHN Modulus-11 Validation (`PhnValidator.cs`)

Implements the official British Columbia Personal Health Number check digit validation algorithm in C#:
- Validates 10-digit length and starting digit `9`.
- Calculates weighted sum using multipliers `[2, 4, 8, 5, 10, 9, 7, 3]`.
- Enforces Modulus-11 check digit verification.
- Includes `MaskPhn()` method for PII-safe log sanitization.

---

## 5. C# HL7 v2 Segment Parser (`Hl7Parser.cs`)

`Hl7Parser.cs` parses raw pipe-delimited HL7 v2 `ADT^A01` messages in C#:
- Extracts `MSH` sending facility and message control ID.
- Extracts `PID-3` (MRN/PHN), `PID-5` (Family/Given Name), and `PID-7` (Birth Date).
- Maps values to `Patient` entity objects.

---

## 6. EF Core 10 PostgreSQL Persistence (`LangleyGeneralDbContext.cs`)

Configures Entity Framework Core 10 with Npgsql PostgreSQL provider:
- Configures unique index on `Mrn` to enforce demographic consistency.
- Manages code-first migrations (`dotnet ef migrations add`).

---

## 7. API Endpoint Reference

### Sync Patient Endpoint
- **POST `/api/langleygeneral/sync`**: Accepts JSON demographic payloads, validates PHN checksums, and performs upsert operations on patient entities.

### FHIR Patient Endpoint
- **GET `/fhir/Patient`**: Serves FHIR R4 Patient JSON resources.
- **GET `/fhir/Patient/{id}`**: Retrieves patient by primary key.

### HL7 Ingest Endpoint
- **POST `/api/langleygeneral/hl7`**: Accepts raw pipe-delimited HL7 v2 messages (`text/plain`).

---

## 8. Local Execution Instructions

```bash
cd LangleyGeneralGateway

# Apply EF Core database migrations
dotnet ef database update

# Run .NET 10 Web API service
dotnet run
```

Service listens on `http://localhost:8083`.
