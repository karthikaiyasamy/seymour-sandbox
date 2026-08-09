# Terry Fox Cancer Hospital — HAPI FHIR R4 Engine & Oncology Node (Java / HAPI FHIR)

`TerryFoxMemorial` represents a specialized regional oncology and clinical research hospital node. Built on **Java 21**, **Spring Boot 3.2.5**, and **HAPI FHIR R4 7.0.2**, it features a native HAPI `RestfulServer` servlet, mCODE oncology data models, dynamic `kid`-driven JWKS public key verification, and audit logging.

---

## 🏛️ Core Responsibilities

1. **Native HAPI FHIR R4 Engine (`RestfulServer`):**
   - Configured in [`TerryFoxHapiServerConfig.java`](src/main/java/com/terryfox/hospital/config/TerryFoxHapiServerConfig.java) serving native FHIR R4 resources under `/fhir/*`.
   - Registers native HAPI FHIR resource providers (`IResourceProvider`) for `Patient`, `Condition`, `ResearchStudy`, `ResearchSubject`, and `DiagnosticReport`.

2. **Dynamic `kid` JWKS Cache Eviction (`TerryFoxJwksKeyService.java`):**
   - Fetches and caches public RSA keys from Seymour Auth Server (`http://localhost:8090/.well-known/jwks.json`).
   - Inspects incoming JWT headers for `kid` (Key ID).
   - Upon encountering an unknown `kid` (triggered by key rotation), automatically logs `[JWKS_CACHE_EVICT]`, evicts RAM cache, and re-fetches public keys on demand without restarting the server.

3. **HAPI Security Interceptor (`TerryFoxSecurityInterceptor.java`):**
   - Hooked into `@Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)` returning `void`.
   - Validates Bearer JWT signatures statelessly and enforces CORS pre-flight bypassing (`RequestTypeEnum.OPTIONS`).

4. **mCODE Cancer Data Models & Genomics Seeding:**
   - **`Condition`**: mCODE cancer staging resources containing ICD-10 codes, primary tumor category (T), regional nodes (N), distant metastasis (M), and stage group.
   - **`ResearchStudy` & `ResearchSubject`**: Clinical trial protocol enrollment data (e.g. EGFR+ Targeted Immunotherapy Trial).
   - **`DiagnosticReport`**: Next-Generation Sequencing (NGS) solid tumor biomarker panel reports (e.g. EGFR Exon 19 Deletion).

---

## ⚙️ Configuration & Properties

Key application properties in [`application.properties`](src/main/resources/application.properties):

```properties
server.port=8085
seymour.auth.jwks-url=http://localhost:8090/.well-known/jwks.json
```

---

## 🧪 Local Execution & Endpoints

```bash
# Compile module
mvn clean compile

# Run Spring Boot service
mvn spring-boot:run
```

- **HAPI Capability Statement:** `http://localhost:8085/fhir/metadata`
- **Patient Resource Endpoint:** `http://localhost:8085/fhir/Patient`
- **Patient Identifier Search:** `http://localhost:8085/fhir/Patient?identifier=MRN-10001`
