# Seymour Regional EHR — Core FHIR & Auth Server

`SeymorFHIR` serves as the primary Electronic Health Record (EHR) node and Identity Provider (IdP) for the regional health network sandbox. Built on Java 21 and Spring Boot 3.2.5, it provides custom FHIR R4 REST APIs, database-backed persistent RSA key management, live key rotation, SMART-on-FHIR OAuth 2.0 authorization endpoints, an HL7 v2 ingestion pipeline, and an Enterprise Master Patient Index (EMPI) identity reconciliation queue.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Persistent RSA Key Store (`OAuthKey.java` & `JwtKeyService.java`)](#2-persistent-rsa-key-store-oauthkeyjava--jwtkeyservicejava)
3. [Zero-Downtime Live Key Rotation (`AdminKeyRotationController.java`)](#3-zero-downtime-live-key-rotation-adminkeyrotationcontrollerjava)
4. [SMART-on-FHIR OAuth2 Authorization Server](#4-smart-on-fhir-oauth2-authorization-server)
   - [SmartConfigurationController.java](#smartconfigurationcontrollerjava)
   - [OAuth2TokenController.java](#oauth2tokencontrollerjava)
   - [TokenStoreService.java (Scheduled RAM Eviction)](#tokenstoreservicejava-scheduled-ram-eviction)
5. [Custom FHIR R4 Clinical REST Controllers](#5-custom-fhir-r4-clinical-rest-controllers)
   - [PatientController.java](#patientcontrollerjava)
   - [ObservationController.java (LOINC Vitals & Labs)](#observationcontrollerjava-loinc-vitals--labs)
   - [AllergyIntoleranceController.java (SNOMED Allergies)](#allergyintolerancecontrollerjava-snomed-allergies)
   - [BundleController.java (Atomic Transaction Bundles)](#bundlecontrollerjava-atomic-transaction-bundles)
6. [HL7 v2 Message Processing & Idempotency Pipeline (`Hl7Service.java`)](#6-hl7-v2-message-processing--idempotency-pipeline-hl7servicejava)
7. [Patient Identity Reconciliation & Conflict Queue (`PatientMatchReviewController.java`)](#7-patient-identity-reconciliation--conflict-queue-patientmatchreviewcontrollerjava)
8. [Configuration Reference (`application.properties`)](#8-configuration-reference-applicationproperties)
9. [Local Execution & API Testing](#9-local-execution--api-testing)

---

## 1. Architecture Overview

Seymour Regional EHR holds the central patient demographic registry and acts as the trusted OAuth2 authorization authority issuing RS256-signed Bearer JWT access tokens.

```
[ Angular SMART App / Integration Engine ]
                   │
                   ├─── 1. GET /.well-known/smart-configuration ───► Metadata
                   ├─── 2. GET /oauth/authorize ─────────────────► Auth Code
                   ├─── 3. POST /oauth/token ─────────────────────► RS256 Bearer JWT
                   │
                   ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                          SEYMOUR REGIONAL EHR (PORT 8090)                              │
│                                                                                        │
│ 1. Database Persistent RSA Key Store (`oauth_keys` table via Flyway V2):              │
│    - PKCS#8 Private Key PEM / X.509 Public Key PEM stored in PostgreSQL               │
│    - Live Signer initialized in memory via `JwtKeyService`                             │
│                                                                                        │
│ 2. Live Key Rotation (`POST /api/admin/rotate-keys`):                                  │
│    - Deactivates previous key entries in PostgreSQL `oauth_keys`                      │
│    - Generates new 2048-bit RSA keypair with timestamped Key ID (`seymour-key-...`)   │
│    - Updates live in-memory signer & publishes new public keys via `/.well-known/jwks.json`│
│                                                                                        │
│ 3. Atomic HL7 v2 Pipeline (`Hl7Service.java`):                                         │
│    - `@Transactional` execution boundary                                               │
│    - MSH-10 control ID & SHA-256 payload hash idempotency checks                      │
│    - Demographic match scoring engine (0.35 - 0.85 triggers `PENDING_REVIEW` queue)    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Persistent RSA Key Store (`OAuthKey.java` & `JwtKeyService.java`)

`JwtKeyService.java` manages RS256 cryptographic keypairs using PostgreSQL persistent storage (`oauth_keys` table).

### Code Walkthrough
- **JPA Entity (`OAuthKey.java`):**
  - Table: `oauth_keys` (configured via Flyway migration `V2__create_oauth_keys_table.sql`).
  - Columns: `keyId` (PK), `privateKeyPem` (TEXT), `publicKeyPem` (TEXT), `algorithm` (`RS256`), `active` (boolean), `createdAt` (Timestamp).
- **Key Initialization (`initKeyPair()`):**
  - Annotations: `@PostConstruct`.
  - Queries `oauthKeyRepository.findByActiveTrue()`.
  - If an active key exists in PostgreSQL: Decodes PKCS#8 private key PEM (`KeyFactory.getInstance("RSA").generatePrivate()`) and X.509 public key PEM (`generatePublic()`) into memory.
  - If no active key exists: Generates a new 2048-bit RSA keypair (`KeyPairGenerator.getInstance("RSA")`), formats keys as PEM strings, persists entry to PostgreSQL, and sets live signer.
- **JWT Generation (`generateSignedSmartJwt(String clientId, String patientId)`):**
  - Builds Nimbus JOSE `RSASSASigner(privateKey)`.
  - Constructs `JWTClaimsSet`: issuer (`http://localhost:8090`), subject (`clientId`), audience (`http://localhost:8090/api/fhir`), patient context (`patientId`), issued-at, and expiration (1 hour).
  - Embeds `kid` in JWT header (`new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(activeKeyId).build()`).
  - Returns serialized signed JWT string.

---

## 3. Zero-Downtime Live Key Rotation (`AdminKeyRotationController.java`)

`AdminKeyRotationController.java` exposes `POST /api/admin/rotate-keys` for triggering zero-downtime key rotation.

### Code Walkthrough
- **Endpoint:** `POST /api/admin/rotate-keys`
- **Method Execution (`jwtKeyService.rotateRsaKeyPair()`):**
  1. Deactivates all existing active key rows in PostgreSQL (`UPDATE oauth_keys SET active = false`).
  2. Generates a fresh 2048-bit RSA keypair with Key ID `seymour-key-<timestamp>`.
  3. Saves new active key entity to PostgreSQL.
  4. Updates in-memory signer and JWKS public key set.
  5. Returns JSON response:
     ```json
     {
       "status": "SUCCESS",
       "message": "RSA Keypair successfully rotated",
       "newKeyId": "seymour-key-1786237319786"
     }
     ```
- **Production Security Guard Posture (Javadoc Note):** In an enterprise production network, this endpoint is restricted via Spring Security `@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")`, Mutual TLS (mTLS) client certificate validation, or API Gateway VPC routing rules.

---

## 4. SMART-on-FHIR OAuth2 Authorization Server

### SmartConfigurationController.java
Exposes SMART-on-FHIR v2.0 discovery metadata.
- **`GET /.well-known/smart-configuration`:** Returns OAuth authorization endpoint (`/oauth/authorize`), token endpoint (`/oauth/token`), JWKS URI (`/.well-known/jwks.json`), supported capabilities (`launch-standalone`, `client-public`), and scopes (`launch/patient`, `patient/*.read`).
- **`GET /.well-known/jwks.json`:** Converts active RSA public key into Nimbus JOSE `JWKSet` JSON object and serves public keys.

### OAuth2TokenController.java
Handles OAuth 2.0 Authorization Code exchange.
- **`GET /oauth/authorize`:** Generates an authorization code (`SMART_AUTH_CODE_<uuid>`), stores code and patient launch context in a thread-safe `ConcurrentHashMap` (`AUTHORIZATION_CODES`), and returns response.
- **`POST /oauth/token`:** Accepts `grant_type=authorization_code`, verifies code in `AUTHORIZATION_CODES`, retrieves patient launch context, calls `jwtKeyService.generateSignedSmartJwt()`, registers token in `tokenStoreService`, and returns access token response.

### TokenStoreService.java (Scheduled RAM Eviction)
Manages active Bearer access tokens.
- **Map Registry:** `ConcurrentHashMap<String, TokenMetadata>` storing token string, patient ID, client ID, and expiration timestamp.
- **Scheduled Eviction Job (`cleanExpiredTokens()`):**
  - Annotation: `@Scheduled(fixedRate = 300000)` (runs every 5 minutes).
  - Evaluates `LocalDateTime.now().isAfter(metadata.expiresAt())`.
  - Evicts expired tokens from memory map, logging `[SCHEDULED_TOKEN_CLEANUP] Evicted N expired OAuth2 token(s)`.

---

## 5. Custom FHIR R4 Clinical REST Controllers

### PatientController.java
- **`GET /api/fhir/Patient`**: Searches active patients by PHN (`healthCardNumber`), MRN, or Family Name. Normalizes PHNs using `PhnValidator.isValidPhn(phn)`.
- **`POST /api/fhir/Patient`**: Registers new patient entity after validating BC PHN Modulus-11 check digit. Rejects invalid PHNs with HTTP 400 Bad Request.

### ObservationController.java (LOINC Vitals & Labs)
- **`GET /api/fhir/Observation/patient/{patientId}`**: Returns LOINC-coded vital signs (e.g. `88371-7` Blood Glucose `28.0 mmol/L`, `4548-4` HbA1c `11.2%`, `8480-6` Systolic Blood Pressure `158 mmHg`).

### AllergyIntoleranceController.java (SNOMED Allergies)
- **`GET /api/fhir/AllergyIntolerance/patient/{patientId}`**: Returns SNOMED CT coded patient allergies (e.g. `91936005` Allergy to Penicillin, `227493005` Allergy to Cashew Nuts).

### BundleController.java (Atomic Transaction Bundles)
- **`POST /api/fhir`**: Processes atomic FHIR R4 `transaction` and `batch` bundles. Iterates over entries, executes child entity creations inside a database transaction, and calls `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()` if any entry fails.

---

## 6. HL7 v2 Message Processing & Idempotency Pipeline (`Hl7Service.java`)

`Hl7Service.java` parses pipe-delimited HL7 v2 ADT (`A01`, `A04`, `A08`) demographic messages.

### Key Implementation Details
- **Transaction Boundary:** Annotated with `@Transactional` for atomic execution.
- **MSH-10 Control ID Idempotency:** Queries `Hl7AuditLogRepository.findByMessageControlId(messageControlId)`. Rejects duplicates.
- **SHA-256 Payload Hash Idempotency:** Calculates SHA-256 hash of raw payload and queries `findByPayloadHash(payloadHash)`. Rejects duplicate payloads with error message referencing original Correlation ID.
- **Audit Logging:** Saves `Hl7AuditLog` entity with lifecycle states: `RECEIVED` -> `VALIDATED` -> `TRANSFORMED` -> `DELIVERED`.

---

## 7. Patient Identity Reconciliation & Conflict Queue (`PatientMatchReviewController.java`)

Seymour calculates a weighted demographic match score ($S \in [0.0, 1.0]$) for inbound HL7 messages:

$$S = S_{\text{LastName}} (0.35) + S_{\text{FirstName}} (0.25) + S_{\text{DOB}} (0.40)$$

- **$S \ge 0.85$**: High confidence match. Automatic association.
- **$0.35 \le S < 0.85$**: Ambiguous conflict. Patient creation is halted, and record is saved in `PatientMatchReview` entity with status `PENDING_REVIEW`.
- **`GET /api/fhir/Patient/match-reviews`**: Health Information Management (HIM) analysts inspect pending conflicts.
- **`POST /api/fhir/Patient/match-reviews/{id}/approve`**: Manually approves conflict, setting status `MANUALLY_APPROVED` and creating the patient record.

---

## 8. Configuration Reference (`application.properties`)

```properties
spring.application.name=seymour-sandbox

# PostgreSQL Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/seymour_db
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:seymour_admin}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:seymour_secure_dev_password_2026}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Flyway Database Migrations
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# Spring Boot Actuator Configuration
management.endpoints.web.exposure.include=health,info

# Server Port
server.port=8090

# SMART on FHIR v2.0 JWT & JWKS Configuration
smart.jwt.key-id=${SMART_JWT_KEY_ID:seymour-smart-key-1}
smart.jwt.issuer=${SMART_JWT_ISSUER:http://localhost:8090}
smart.jwt.audience=${SMART_JWT_AUDIENCE:http://localhost:8090/api/fhir}
```

---

## 9. Local Execution & API Testing

### Build & Run
```bash
# Compile and run test suite
mvn clean test

# Run Spring Boot service
mvn spring-boot:run
```

### Key API Curl Commands

#### 1. Fetch SMART Discovery Metadata
```bash
curl -X GET http://localhost:8090/.well-known/smart-configuration
```

#### 2. Fetch JWKS Public Key Set
```bash
curl -X GET http://localhost:8090/.well-known/jwks.json
```

#### 3. Trigger Live Emergency RSA Key Rotation
```bash
curl -X POST http://localhost:8090/api/admin/rotate-keys
```

#### 4. Actuator Health Probe
```bash
curl -X GET http://localhost:8090/actuator/health
```
