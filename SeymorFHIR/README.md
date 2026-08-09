# Seymour Regional EHR — Core FHIR & Auth Server (Java / Spring Boot)

`SeymorFHIR` serves as the central Electronic Health Record (EHR) node and primary Identity Provider (IdP) for the regional health network sandbox. Built on **Java 21** and **Spring Boot 3.2.5**, it provides custom FHIR R4 REST APIs, database-backed persistent RSA key management, live key rotation capabilities, and SMART-on-FHIR OAuth 2.0 authorization endpoints.

---

## 🏛️ Core Responsibilities

1. **Persistent RSA Key Management (`oauth_keys`):**
   - Stores 2048-bit RSA keypairs serialized as PKCS#8 / X.509 PEM strings in PostgreSQL `oauth_keys` table via Flyway migration [`V2__create_oauth_keys_table.sql`](src/main/resources/db/migration/V2__create_oauth_keys_table.sql).
   - Reuses active RSA keypairs across application restarts, ensuring RS256 Bearer JWT signatures remain persistent.

2. **Zero-Downtime RSA Key Rotation (`POST /api/admin/rotate-keys`):**
   - Admin controller ([`AdminKeyRotationController.java`](src/main/java/com/healthcare/sandbox/controller/AdminKeyRotationController.java)) allowing emergency or scheduled key rotation.
   - Deactivates older DB key entries, generates a fresh 2048-bit RSA keypair with a timestamped Key ID (`seymour-key-...`), updates the in-memory signer, and publishes new public keys via `/.well-known/jwks.json`.

3. **SMART-on-FHIR OAuth2 Authorization Server:**
   - Exposes SMART discovery metadata at `/.well-known/smart-configuration`.
   - Issues RS256-signed Bearer JWT access tokens containing client ID, patient context, and scopes (`patient/*.read`).
   - Supports dual `application/x-www-form-urlencoded` and `application/json` token request bodies ([`OAuth2TokenController.java`](src/main/java/com/healthcare/sandbox/controller/OAuth2TokenController.java)).

4. **Custom FHIR R4 Clinical REST APIs:**
   - **`GET /api/fhir/Patient`**: Search patients by identifier (PHN/MRN), family name, or ID.
   - **`GET /api/fhir/Observation`**: Retrieve LOINC-coded vital signs and lab results.
   - **`GET /api/fhir/AllergyIntolerance`**: Retrieve SNOMED-coded patient allergy records.
   - **`POST /api/fhir`**: Atomic FHIR transaction bundle processor with database rollback enforcement.

---

## ⚙️ Configuration & Properties

Key application properties in [`application.properties`](src/main/resources/application.properties):

```properties
server.port=8090
spring.datasource.url=jdbc:postgresql://localhost:5432/seymour_db
smart.jwt.issuer=http://localhost:8090
smart.jwt.audience=http://localhost:8090/api/fhir
smart.jwt.key-id=seymour-smart-key-1
```

---

## 🧪 Local Execution & Endpoints

```bash
# Compile module
mvn clean compile

# Run Spring Boot service
mvn spring-boot:run
```

- **Swagger UI Developer Portal:** `http://localhost:8090/swagger-ui.html`
- **JWKS Public Key Endpoint:** `http://localhost:8090/.well-known/jwks.json`
- **SMART Discovery Endpoint:** `http://localhost:8090/.well-known/smart-configuration`
