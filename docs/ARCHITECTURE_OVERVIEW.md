# Regional Health Interoperability & Federated Security Architecture

This document outlines the architectural blueprint of the Regional Healthcare Interoperability Sandbox, designed for federated clinical data exchange, SMART-on-FHIR authorization, zero-downtime RSA key management, and Enterprise Master Patient Index (EMPI) identity resolution.

```
                               ┌─────────────────────────────────────────────────────────┐
                               │                Seymour Regional EHR                     │
                               │          (Java / Spring Boot / Custom REST)             │
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
│           (Port 4200 / SPA)             │   │    (Java 21 / HAPI FHIR R4 Engine)      │
│  - Search Directory & Quick Selection   │   │  Port: 8085                             │
│  - Cross-Node Federated Patient Search  │───│  - Native HAPI RestfulServer            │
│  - EMPI Identity Reconciliation Engine  │   │  - `kid`-Driven JWKS Cache Eviction     │
│  - Emergency Key Rotation Trigger Button│   │  - mCODE Oncology & Genomics Suite      │
└─────────────────────────────────────────┘   └─────────────────────────────────────────┘
```

---

## 1. Core Architectural Principles

1. **Federated Identity & Stateless Authorization:**
   Seymour Regional EHR operates as the primary Identity Provider (IdP) issuing signed RS256 Bearer JWTs. External specialty nodes (e.g., Terry Fox Cancer Hospital) verify tokens statelessly using Seymour's JWKS endpoint.

2. **Zero-Downtime RSA Key Rotation:**
   Central keypairs stored in PostgreSQL (`oauth_keys`) can be rotated on demand. Remote nodes inspect the JWT header's `kid` (Key ID) and dynamically evict their RAM cache upon encountering a new key ID.

3. **Enterprise Master Patient Index (EMPI) Reconciliation:**
   Regional nodes match patients by unique identifiers (e.g., BC Personal Health Number). When demographic discrepancies occur (e.g., 3-day Date of Birth delta), the UI computes a match confidence score and flags records for administrative review rather than silently dropping data.

4. **Multi-Stack Healthcare Interoperability:**
   Demonstrates interoperability across Java Spring Boot, HAPI FHIR R4, C# .NET 10 Web API, and Angular 17 SPA, supporting both HL7 v2 and FHIR R4 data pipelines.
