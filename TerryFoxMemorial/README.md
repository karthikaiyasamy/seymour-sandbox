# Terry Fox Cancer Hospital — HAPI FHIR R4 Engine & Oncology Node

`TerryFoxMemorial` serves as a specialized regional oncology and clinical research hospital node within the federated health authority sandbox. Built on Java 21, Spring Boot 3.2.5, and HAPI FHIR R4 (7.0.2), it features a native HAPI `RestfulServer` servlet, mCODE oncology data models, dynamic key-ID (`kid`) driven JWKS public key verification, and an automated HL7 v2 pathology ingestion pipeline.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [HAPI FHIR Servlet Configuration (`TerryFoxHapiServerConfig.java`)](#2-hapi-fhir-servlet-configuration-terryfoxhapiserverconfigjava)
3. [HAPI FHIR Resource Providers](#3-hapi-fhir-resource-providers)
   - [PatientResourceProvider.java](#patientresourceproviderjava)
   - [ConditionResourceProvider.java (mCODE Cancer Staging)](#conditionresourceproviderjava-mcode-cancer-staging)
   - [ResearchStudyResourceProvider.java (Clinical Trial Protocols)](#researchstudyresourceproviderjava-clinical-trial-protocols)
   - [ResearchSubjectResourceProvider.java (Trial Enrollment)](#researchsubjectresourceproviderjava-trial-enrollment)
   - [DiagnosticReportResourceProvider.java (NGS Genomics & Pathology)](#diagnosticreportresourceproviderjava-ngs-genomics--pathology)
4. [Stateless JWKS Security Interceptor (`TerryFoxSecurityInterceptor.java`)](#4-stateless-jwks-security-interceptor-terryfoxsecurityinterceptorjava)
5. [Dynamic Key ID (`kid`) Cache Eviction Engine (`TerryFoxJwksKeyService.java`)](#5-dynamic-key-id-kid-cache-eviction-engine-terryfoxjwkskeyservicejava)
6. [HL7 v2 Pathology & Genomic Ingestion Controller (`Hl7OncologyIngestController.java`)](#6-hl7-v2-pathology--genomic-ingestion-controller-hl7oncologyingestcontrollerjava)
7. [Synthetic Data Seeding (`TerryFoxDataSeeder.java`)](#7-synthetic-data-seeding-terryfoxdataseederjava)
8. [Comprehensive Unit & Functional Test Suite](#8-comprehensive-unit--functional-test-suite)
9. [Configuration Reference (`application.properties`)](#9-configuration-reference-applicationproperties)
10. [Local Execution & API Testing](#10-local-execution--api-testing)

---

## 1. Architecture Overview

The Terry Fox Cancer Hospital node operates as an autonomous regional specialty facility. It relies on Seymour Regional EHR as its central Identity Provider (IdP) for issuing RS256 Bearer JWT access tokens, but performs cryptographic token verification statelessly using Seymour's public JSON Web Key Set (JWKS) endpoint.

```
[ Angular SMART Portal / Client ]
              │
              ├─── HTTP GET /fhir/Patient?identifier=9234567897 (Bearer Token) ───┐
              │                                                                    │
              ▼                                                                    ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        TERRY FOX HAPI FHIR NODE (PORT 8085)                            │
│                                                                                        │
│ 1. HAPI Interceptor (`TerryFoxSecurityInterceptor`): Pre-handled void hook              │
│    - Checks method: OPTIONS pre-flight requests bypass auth                             │
│    - Checks path: /metadata, /v3/api-docs open paths bypass auth                      │
│    - Extracts `Authorization: Bearer <token>`                                          │
│                                                                                        │
│ 2. Key Verification (`TerryFoxJwksKeyService`):                                        │
│    - Parses unencrypted JWT header → Reads `kid` (e.g. `seymour-key-1786...`)           │
│    - Checks `ConcurrentHashMap<String, RSAPublicKey>` RAM cache                        │
│    - If `kid` missing → Logs `[JWKS_CACHE_EVICT]`, fetches Seymour `/.well-known/jwks.json`│
│    - Verifies RS256 RSA signature using active public key                              │
│                                                                                        │
│ 3. Native HAPI Provider Execution (`PatientResourceProvider`):                          │
│    - Queries H2/PostgreSQL database via `PatientRepository`                            │
│    - Maps JPA Entity to HAPI FHIR `org.hl7.fhir.r4.model.Patient` object               │
│    - Returns HTTP 200 OK with FHIR R4 JSON Bundle                                      │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. HAPI FHIR Servlet Configuration (`TerryFoxHapiServerConfig.java`)

`TerryFoxHapiServerConfig.java` manages Spring Boot servlet registration for the HAPI FHIR framework and configures HTTP client connection timeouts.

### Code Walkthrough & Key Definitions
- **Class Annotations:** `@Configuration`, `@RequiredArgsConstructor` (constructor injection for all final dependencies).
- **Servlet Bean (`fhirServlet`):**
  - Instantiates `RestfulServer` configured with `FhirContext.forR4()`.
  - Sets Server Name: `Terry Fox Memorial Hospital - HAPI FHIR R4 Engine`.
  - Sets Server Version: `1.0.0-ONCOLOGY`.
  - Sets Default Encoding: `EncodingEnum.JSON`.
  - Registers Resource Providers: `PatientResourceProvider`, `ConditionResourceProvider`, `ResearchStudyResourceProvider`, `ResearchSubjectResourceProvider`, `DiagnosticReportResourceProvider`.
  - Configures `CorsInterceptor`: Allows all origins, methods, and headers (`*`).
  - Registers Interceptors: `TerryFoxAuditInterceptor` (logging) and `TerryFoxSecurityInterceptor` (JWT security).
  - Binds Servlet to route pattern `/fhir/*` via `ServletRegistrationBean<RestfulServer>`.
- **HTTP Client Bean (`restTemplate`):**
  - Uses `RestTemplateBuilder` to construct a Spring `RestTemplate` bean.
  - Sets Connect Timeout: `Duration.ofSeconds(3)`.
  - Sets Read Timeout: `Duration.ofSeconds(5)`.
  - Ensures remote HTTP calls to Seymour's JWKS endpoint never block HAPI servlet worker threads indefinitely.

---

## 3. HAPI FHIR Resource Providers

All resource providers implement HAPI FHIR's `IResourceProvider` interface and return `Class<? extends IBaseResource>`.

### PatientResourceProvider.java
Provides FHIR R4 `Patient` resource operations.
- **`getResourceType()`:** Returns `Patient.class`.
- **`@Read public Patient read(@IdParam IdType theId)`:**
  - Parses ID as long via `theId.getIdPartAsLong()`.
  - Throws HAPI `ResourceNotFoundException` (HTTP 404) if ID is missing or invalid.
  - Fetches `PatientEntity` from `PatientRepository`.
  - Maps demographics, BC PHN identifier (`http://sharedhealth.exchange/fhir/NamingSystem/ca-bc-patient-phn`), MRN (`http://terryfox.hospital/mrn`), given/family names, birth date, gender, address, phone, and primary oncologist.
- **`@Search public List<Patient> search(@OptionalParam TokenParam theIdentifier, @OptionalParam StringParam theFamily)`:**
  - If `theIdentifier` is provided: Searches `patientRepository.findByPhn(value)` first, falling back to `patientRepository.findByMrn(value)`.
  - If `theFamily` is provided: Searches `patientRepository.findByFamilyNameContainingIgnoreCase(value)`.
  - If parameters are null: Returns all active patient entities.

### ConditionResourceProvider.java (mCODE Cancer Staging)
Provides mCODE-compliant cancer primary condition resources.
- **`getResourceType()`:** Returns `Condition.class`.
- **`@Read public Condition read(@IdParam IdType theId)`:**
  - Fetches `ConditionEntity` from `ConditionRepository`.
  - Sets clinical status (`active`), verification status (`confirmed`), category (`problem-list-item`), and code (ICD-10 / SNOMED CT for Breast Cancer or Non-Small Cell Lung Cancer).
  - Attaches mCODE TNM Staging extensions:
    - Primary Tumor (T): `T2` / `T3`
    - Regional Nodes (N): `N1` / `N2`
    - Distant Metastasis (M): `M0` / `M1`
    - Stage Group: `Stage IIIA` / `Stage IV`

### ResearchStudyResourceProvider.java (Clinical Trial Protocols)
Provides clinical trial protocol metadata for oncology research.
- **`getResourceType()`:** Returns `ResearchStudy.class`.
- **`@Read public ResearchStudy read(@IdParam IdType theId)`:**
  - Returns trial protocol details (e.g. `TF-TRIALS-2026-EGFR` — Phase III EGFR-Mutated NSCLC Targeted Immunotherapy).
  - Sets status (`active`), primary sponsor (`Terry Fox Cancer Research Institute`), focus (Targeted Therapy), and eligibility criteria.

### ResearchSubjectResourceProvider.java (Trial Enrollment)
Provides patient clinical trial enrollment records.
- **`getResourceType()`:** Returns `ResearchSubject.class`.
- **`@Read public ResearchSubject read(@IdParam IdType theId)`:**
  - Links a `Patient` reference (`Patient/2`) to a `ResearchStudy` reference (`ResearchStudy/1`).
  - Sets status (`on-study`) and assigned arm (`Arm A: Osimertinib + Chemotherapy`).

### DiagnosticReportResourceProvider.java (NGS Genomics & Pathology)
Provides Next-Generation Sequencing (NGS) molecular pathology reports.
- **`getResourceType()`:** Returns `DiagnosticReport.class`.
- **`@Read public DiagnosticReport read(@IdParam IdType theId)`:**
  - Fetches `GenomicReportEntity` from `GenomicReportRepository`.
  - Sets LOINC code (`21008-9` — Genomic Pathology Panel).
  - Attaches specimen source (`Tissue Biopsy`), tested gene target (`EGFR Exon 19 Deletion`), mutation result (`Pathogenic Variant Detected`), and pathologist conclusions.

---

## 4. Stateless JWKS Security Interceptor (`TerryFoxSecurityInterceptor.java`)

`TerryFoxSecurityInterceptor.java` enforces OAuth2 Bearer token authentication at the HAPI framework level before any resource provider is executed.

### Code Walkthrough
- **Annotations:** `@Component`, `@Interceptor`, `@RequiredArgsConstructor`, `@Slf4log`.
- **Hook Pointcut:** `@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED) public void incomingRequestPreHandled(RequestDetails requestDetails)`
  - Must return `void` (required by HAPI FHIR architecture).
- **CORS Pre-Flight Bypass:**
  - Safely casts `RequestDetails` to `ServletRequestDetails` to extract raw Tomcat `HttpServletRequest`.
  - Checks if `requestDetails.getRequestType() == RequestTypeEnum.OPTIONS` or request method is `OPTIONS`. If true, returns immediately (bypassing authentication for CORS pre-flights).
- **Open Path Whitelist:**
  - Checks if request path contains `/metadata`, `/v3/api-docs`, or `/swagger-ui`. If true, returns immediately.
- **Bearer Token Extraction:**
  - Reads `Authorization` header. If missing or does not start with `Bearer `, logs warning and throws HAPI `AuthenticationException` (HTTP 401 Unauthorized).
- **Cryptographic Verification:**
  - Trims token string and delegates verification to `jwksKeyService.verifySignedJwt(token)`.
  - If claims return `null`, logs `[TERRY_FOX_AUTH_REJECTED]` and throws `AuthenticationException` (HTTP 401).
  - If valid, logs `[TERRY_FOX_AUTH_SUCCESS]` and permits request execution.

---

## 5. Dynamic Key ID (`kid`) Cache Eviction Engine (`TerryFoxJwksKeyService.java`)

`TerryFoxJwksKeyService.java` verifies RS256 Bearer JWT signatures against Seymour Auth Server's JWKS endpoint with zero-downtime key rotation support.

### Key Class Logic
- **RAM Cache:** `private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();`
- **`getPublicKey(String keyId)` Algorithm:**
  1. Checks if `keyId != null` and `keyCache.containsKey(keyId)`: Returns cached `RSAPublicKey` in sub-millisecond time.
  2. If `keyId` is unmapped (cache miss): Logs `[JWKS_CACHE_EVICT] Unknown keyId [{}] presented in JWT header. Evicting cache and re-fetching JWKS...` and invokes `refreshJwksCache()`.
  3. Returns newly cached public key matching `keyId`.
- **`refreshJwksCache()` Method:**
  - Executes `restTemplate.getForObject(jwksUrl, String.class)` to fetch JWKS JSON string.
  - Parses JSON string using Nimbus JOSE `JWKSet.parse(jwksJson)`.
  - Iterates over keys: Converts each `RSAKey` to `RSAPublicKey` (`rsaJwk.toRSAPublicKey()`) and updates `keyCache.put(keyId, publicKey)`.
  - Logs `[JWKS_CACHE_UPDATED] Successfully cached N public RSA key(s)`.
- **`verifySignedJwt(String token)` Method:**
  - Parses token via `SignedJWT.parse(token)`.
  - Reads header `keyId = signedJWT.getHeader().getKeyID()`.
  - Resolves `RSAPublicKey` via `getPublicKey(keyId)`.
  - Verifies signature using Nimbus JOSE `RSASSAVerifier(publicKey)`.
  - Checks token expiration (`new Date().after(claims.getExpirationTime())`).
  - Returns `JWTClaimsSet` on success, `null` on failure.

---

## 6. HL7 v2 Pathology & Genomic Ingestion Controller (`Hl7OncologyIngestController.java`)

Exposes `POST /api/terryfox/hl7` to ingest raw HL7 v2 `ORU^R01` pathology and Next-Generation Sequencing (NGS) genomic lab result messages.

### Logic Flow
1. Receives raw pipe-delimited HL7 message string (`text/plain`, `application/x-hl7`).
2. Splits payload by line breaks into segments (`MSH`, `PID`, `OBR`, `OBX`).
3. Extracts `PID-3` (BC Personal Health Number) and `PID-5` (Patient Given/Family Name).
4. Validates PHN checksum using `PhnValidator.isValidPhn(phn)`. If invalid, returns HTTP 400 Bad Request with `status: "REJECTED"`.
5. Queries `PatientRepository.findByPhn(phn)`. Registers new `PatientEntity` if missing.
6. Extracts `OBR-4` (Report Title), `OBX-3` (Gene Target), and `OBX-5` (Mutation Result).
7. Persists `GenomicReportEntity` linked to `PatientEntity` in database.
8. Returns HTTP 200 OK JSON:
   ```json
   {
     "status": "SUCCESS",
     "phn": "923****897",
     "genomicReportId": 101,
     "fhirReference": "DiagnosticReport/101"
   }
   ```

---

## 7. Synthetic Data Seeding (`TerryFoxDataSeeder.java`)

On startup, `TerryFoxDataSeeder.java` seeds synthetic oncology patient records if the database is empty:

1. **Margaret Chen (MRN-10001 / BC9001234567):**
   - Diagnosis: Triple-Negative Breast Cancer (TNBC), Stage IIIA (`T3N1M0`).
   - Genomic Profile: BRCA1 Pathogenic Germline Mutation Detected.
2. **Sarah Jenkins (MRN-10002 / 9234567897):**
   - Diagnosis: Non-Small Cell Lung Cancer (NSCLC), Stage IV (`T2N2M1`).
   - Genomic Profile: EGFR Exon 19 Deletion Detected (Targeted Therapy Eligible).

---

## 8. Comprehensive Unit & Functional Test Suite

Located in `src/test/java/com/terryfox/hospital/`:

| Test Class | Category | Primary Focus |
| :--- | :--- | :--- |
| **`TerryFoxJwksKeyServiceTest`** | Unit Test | RS256 JWT signature verification, mock JWKS JSON parsing, dynamic `kid` cache eviction, expired token rejection, tampered signature rejection. |
| **`TerryFoxSecurityInterceptorTest`** | Unit Test | HAPI FHIR `@Hook` pre-handled interceptor, CORS `OPTIONS` pre-flight bypass, missing `Authorization: Bearer` header HTTP 401 rejection, valid token pass-through. |
| **`PatientResourceProviderTest`** | Functional Test | HAPI FHIR `@Read` (`Patient/1`) and `@Search` (`Patient?identifier=MRN-10001` / `9234567897`), BC PHN identifier mapping. |
| **`Hl7OncologyIngestControllerTest`** | Integration Test | `POST /api/terryfox/hl7` ingestion of raw `ORU^R01` pathology messages, BC PHN Modulus-11 checksum validation. |

---

## 9. Configuration Reference (`application.properties`)

```properties
server.port=8085
spring.application.name=terry-fox-memorial

# Seymour Regional EHR Auth Server JWKS Endpoint
seymour.auth.jwks-url=http://localhost:8090/.well-known/jwks.json

# H2 In-Memory Database (Default Local Sandbox Mode)
spring.datasource.url=jdbc:h2:mem:terryfox_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update

# Spring Boot Actuator Exposure
management.endpoints.web.exposure.include=health,info
```

---

## 10. Local Execution & API Testing

### Build & Run
```bash
# Compile and run unit tests
mvn clean test

# Run Spring Boot service
mvn spring-boot:run
```

### API Curl Test Commands

#### 1. Fetch HAPI Capability Statement
```bash
curl -X GET http://localhost:8085/fhir/metadata
```

#### 2. Query Patient by PHN (Requires Valid Bearer Token from Seymour Auth)
```bash
curl -X GET "http://localhost:8085/fhir/Patient?identifier=9234567897" \
  -H "Authorization: Bearer <VALID_SEYMOUR_JWT_TOKEN>"
```

#### 3. Ingest Raw HL7 v2 Pathology Report
```bash
curl -X POST http://localhost:8085/api/terryfox/hl7 \
  -H "Content-Type: text/plain" \
  -d $'MSH|^~\\&|BC_CANCER_LAB|VANCOUVER_CENTER|TERRY_FOX|MAIN_FACILITY|20260808120000||ORU^R01^ORU_R01|MSG-PATH-9001|P|2.4\nPID|1||9234567897^^^PHN||Jenkins^Sarah||19761123|F\nOBR|1|ORD-2026-88|LAB-9901|21008-9^Genomic Pathology Panel^LN|||20260808113000\nOBX|1|TX|EGFR-01^EGFR Mutation Analysis||EGFR Exon 19 Deletion Detected (Pathogenic)||F'
```

#### 4. Actuator Health Probe
```bash
curl -X GET http://localhost:8085/actuator/health
```
