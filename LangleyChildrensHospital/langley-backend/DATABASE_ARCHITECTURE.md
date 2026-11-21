# Langley Children's Hospital: Database Architecture Guide

This document details the relational database architecture for the pediatric synchronization platform (`langley_db`), provides a technical comparison between MySQL and PostgreSQL, and outlines the rationale behind selecting PostgreSQL for this healthcare system.

---

## 1. Relational Schema Architecture

The database `langley_db` runs on PostgreSQL 14 (port 5432) and serves as the persistence tier for `langley-backend`. It models core pediatric demographics, clinical encounters, immunization records, diagnostic laboratory panels, and allergy profiles.

### Entity Relationship Diagram

```
 ┌────────────────────────────────────────────────────────┐
 │                   langley_patients                     │
 ├────────────────────────────────────────────────────────┤
 │ PK  id                   BIGINT                        │
 │ UQ  mrn                  VARCHAR(255)  [Unique Index]  │
 │ UQ  seymour_patient_id   VARCHAR(255)  [Unique Index]  │
 │     first_name           VARCHAR(255)                  │
 │     last_name            VARCHAR(255)                  │
 │     date_of_birth        DATE                          │
 │     gender               VARCHAR(255)                  │
 │     health_card_number   VARCHAR(255)  [BC PHN]        │
 │     blood_type           VARCHAR(255)                  │
 │     allergies            VARCHAR(255)                  │
 │     admitted             BOOLEAN                       │
 │     phone                VARCHAR(255)                  │
 │     email                VARCHAR(255)                  │
 │     created_at           TIMESTAMP                     │
 └───────────┬───────────────────┬────────────────────┬───┘
             │ 1                 │ 1                  │ 1
             │                   │                    │
             │ N                 │ N                  │ N
             ▼                   ▼                    ▼
 ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
 │ langley_vaccinations  │ │  langley_lab_results  │ │   langley_allergies   │
 ├───────────────────────┤ ├───────────────────────┤ ├───────────────────────┤
 │ PK id         BIGINT  │ │ PK id         BIGINT  │ │ PK id         BIGINT  │
 │ FK patient_id BIGINT  │ │ FK patient_id BIGINT  │ │ FK patient_id BIGINT  │
 │    vaccine_name  TEXT │ │    test_name     TEXT │ │    allergy_display TEXT│
 │    vaccine_code  TEXT │ │    test_code     TEXT │ │    allergy_code    TEXT│
 │    lot_number    TEXT │ │    result_value  TEXT │ │    category        TEXT│
 │    admin_date    TIME │ │    unit          TEXT │ │    criticality     TEXT│
 │    administered_by TEXT││    flag (N/A)    TEXT │ │    reaction        TEXT│
 │    created_at    TIME │ │    test_date     TIME │ │    synced_at       TIME│
 └───────────────────────┘ └───────────────────────┘ └───────────────────────┘
```

---

## 2. Table Specifications and Constraints

### A. `langley_patients` (Master Patient Index)
* **Primary Key:** `id` (64-bit integer, generated identity).
* **Unique Constraints:**
  * `uk_5ly2g5my5p55e9178r9tdutnq` on `mrn`: Enforces strict medical record number uniqueness.
  * `uk_okk9y8xfwrgly7tpjiybrtunj` on `seymour_patient_id`: Guarantees a 1:1 mapping between regional central EHR IDs and local pediatric records.
* **Date Storage:** `date_of_birth` uses the native PostgreSQL `DATE` type rather than `TIMESTAMP`. A birthdate is a calendar date without a time or timezone offset. Storing it as `DATE` prevents timezone conversions from shifting a patient birthdate backward by one day when queried across Pacific and Eastern timezones.

### B. `langley_vaccinations` (Immunization History)
* **Primary Key:** `id` (bigint).
* **Foreign Key:** `patient_id` references `langley_patients(id)` with referential integrity.
* **Clinical Coding:** Stores standard CVX vaccine codes (for example, `03` for Measles, Mumps, Rubella) alongside manufacturer lot numbers for pediatric safety tracking.

### C. `langley_lab_results` (Diagnostic Panels)
* **Primary Key:** `id` (bigint).
* **Foreign Key:** `patient_id` references `langley_patients(id)`.
* **Clinical Coding:** Stores standardized LOINC codes (for example, `100-8` for Blood Lead Level) and flags (`N` for Normal, `A` for Abnormal).

### D. `langley_allergies` (Safety Precautions)
* **Primary Key:** `id` (bigint).
* **Foreign Key:** `patient_id` references `langley_patients(id)`.
* **Clinical Attributes:** Categorizes criticality (`low`, `high`, `fatal`) and tracks clinical reactions (`anaphylaxis`, `rash`).

---

## 3. Technical Comparison: MySQL vs. PostgreSQL

Both MySQL and PostgreSQL are mature, production-grade relational database management systems. However, their internal engines, design philosophies, and architectural trade-offs differ significantly:

| Feature / Dimension | MySQL (InnoDB) | PostgreSQL |
| :--- | :--- | :--- |
| **Philosophy & Focus** | Read performance, horizontal web scaling, simplicity. | Standards compliance, extensibility, strict data integrity. |
| **SQL Standards Compliance** | Partial. Historically permitted non-standard SQL behaviors (such as loose `GROUP BY` handling and silent string truncations). | Very high. Conforms strictly to ANSI SQL:2016 standards. Rejects invalid data aggressively. |
| **Data Types** | Traditional SQL types (VARCHAR, INT, DATETIME, BLOB). Basic JSON support. | Advanced native types: UUID, Arrays, Network types (INET), Geometry (PostGIS), Range types, and JSONB. |
| **Semi-Structured Data (JSON)** | Stores JSON as binary text. Functions require scanning the document. | Native **JSONB** (decomposed binary format) with GIN/GiST indexing. Supports indexing individual keys inside JSON. |
| **Concurrency Control (MVCC)** | InnoDB uses Undo Logs inside rollback segments. High write concurrency can build up undo log contention. | Native Multi-Version Concurrency Control (MVCC). Readers never block writers, and writers never block readers. |
| **Transactional DDL** | No. DDL statements (`ALTER TABLE`, `CREATE INDEX`) commit automatically and cannot be rolled back inside a transaction. | **Yes.** Schema migrations can run inside a `BEGIN ... COMMIT` block. If a migration fails halfway, it rolls back cleanly. |
| **Index Types Supported** | B-Tree, Hash, Spatial, Full-Text. | B-Tree, Hash, GIN, GiST, BRIN (block range index), and Partial / Expression-based Indexes. |
| **Extensibility** | Pluggable storage engines, limited custom extensions. | Highly extensible. Supports custom data types, operators, index methods, and procedural languages (PL/pgSQL, PL/Python). |

---

## 4. Why PostgreSQL Was Selected for This Healthcare System

In clinical and healthcare IT systems (such as Cerner Millennium, Epic, and HAPI FHIR server platforms), data integrity and standards compliance take precedence over raw web read speeds. 

PostgreSQL was selected for this architecture based on the following key engineering requirements:

### 1. Zero Tolerance for Silent Data Truncation
In healthcare, patient safety depends on absolute data correctness. If an HL7 interface sends a patient name or medication dosage that exceeds a column length, MySQL historically had modes where it would silently truncate the string and store partial data. PostgreSQL enforces strict type checking: it rejects invalid data immediately with an error, preventing corrupted medical records from entering the database.

### 2. Transactional DDL for Safe Database Migrations
When upgrading healthcare systems in hospital environments, downtime windows are minimal. 
* In MySQL, running an `ALTER TABLE` cannot be rolled back. If a script fails on step 4 of 5, the database is left in a partially migrated, broken state.
* In PostgreSQL, database migrations (such as Flyway or Entity Framework Core migrations) run inside a single transaction. If a constraint or column creation fails, the entire transaction rolls back cleanly, leaving the patient database untouched.

### 3. Native Support for Semi-Structured FHIR Payloads (JSONB)
Healthcare is transitioning from legacy pipe-delimited HL7 v2 messages to HL7 FHIR (Fast Healthcare Interoperability Resources) formatted as JSON documents. 
* PostgreSQL provides `JSONB`, a binary representation that parses JSON once and allows indexing specific nested fields (such as patient identifiers or LOINC observation codes) using Generalized Inverted Indexes (GIN).
* This allows the system to store both structured relational tables (demographics) and semi-structured clinical documents (FHIR resource bundles) in the same database engine without needing a separate NoSQL store like MongoDB.

### 4. Advanced Indexing: Partial Indexes for Clinical Flags
In clinical databases, abnormal results or critical alerts often represent less than 1 percent of all records. PostgreSQL allows creating **Partial Indexes**:
```sql
CREATE INDEX idx_critical_labs ON langley_lab_results (patient_id) WHERE flag = 'A';
```
This index only stores pointers to rows where the lab is abnormal, remaining tiny in memory and allowing emergency clinician queries to run in microseconds without scanning millions of normal lab rows.

### 5. Multi-Version Concurrency Control (MVCC) Without Read Locks
Hospitals operate 24/7. Intensive reporting queries (such as ministry infection tracking or pediatric vaccine coverage audits) cannot lock the tables and prevent emergency room nurses from saving admission records. PostgreSQL's MVCC ensures that long-running analytical queries read a consistent snapshot of the data while new patient admissions write concurrently without blocking.

### 6. Alignment with Provincial and Cerner Healthcare Standards
Provincial health authorities (such as PHSA and Fraser Health) and open-source healthcare platforms (such as the HAPI FHIR reference server) standardize on PostgreSQL and Oracle. Using PostgreSQL locally ensures full parity with hospital production environments.
