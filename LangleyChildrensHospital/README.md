# Langley Children's Hospital Workspace

This workspace contains the pediatric portal system for Langley Children's Hospital, serving as a data consumer for pediatric admissions, immunizations, and lab profiles.

---

## Component Architecture

1. **`langley-backend` (Java / Spring Boot: Port 8081):**
   - Receives inbound pediatric demographic sync payloads forwarded by Mirth Connect middleware via `POST /api/langley/pediatric/sync`.
   - Validates BC PHN Modulus-11 checksums using `PhnValidator.java`.
   - Persists pediatric patient entities, immunization records, and allergy profiles in PostgreSQL (`langley_db`).
   - Serves REST APIs (`/api/patients`) for the clinician frontend.
   - See database schema specifications and MySQL vs PostgreSQL trade-offs in [DATABASE_ARCHITECTURE.md](./DATABASE_ARCHITECTURE.md).

2. **`langley-frontend` (React / Vite Dashboard: Port 8082):**
   - Single-Page Application (SPA) built with React and Vite following BC Cancer / PHSA healthcare design.
   - Provides real-time polling to fetch synced pediatric patient records, immunizations, and allergy profiles.
   - See detailed architectural walkthrough and learning guide in [ARCHITECTURE.md](./langley-frontend/ARCHITECTURE.md).

---

## Local Execution Instructions

### Run Backend (Port 8081)
```bash
cd LangleyChildrensHospital/langley-backend
mvn spring-boot:run
```

### Run Frontend (Port 8082)
```bash
cd LangleyChildrensHospital/langley-frontend
npm install
npm run dev
```
