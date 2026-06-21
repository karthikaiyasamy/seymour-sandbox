# Langley Children's Hospital Workspace (LangleyChildrensHospital)

This directory contains the Langley Children's Hospital portal system. In our regional healthcare interoperability pipeline, Langley acts as the data consumer—receiving patient registration, vaccination, and lab result updates forwarded from other clinics via the integration engine (Mirth Connect).

It consists of two main components:
1. **langley-backend** (Spring Boot service running on port 8081)
2. **langley-frontend** (React/Vite single-page dashboard application)

---

## 1. Langley Backend (`langley-backend`)

The backend is responsible for receiving inbound data from the integration engine, parsing it, and persisting it to its own local clinical registry.

### Core Roles
* **Sync Webhook Endpoint:** Exposes `POST /api/langley/pediatric/sync` to receive FHIR R4 JSON payloads forwarded by Mirth Connect.
* **Internal APIs:** Exposes REST endpoints for the React frontend to fetch synced patient demographics, vaccinations, and lab results.

### Tech Stack
* **Java 21** & **Spring Boot**
* **Spring Data JPA** & **Hibernate**
* **PostgreSQL** (Database name: `langley_db`)
* **Server Port:** `8081`

---

## 2. Langley Frontend (`langley-frontend`)

A real-time administrative dashboard that allows clinicians at Langley Children's Hospital to monitor patient sync events.

### Core Roles
* **Patient Sync Console:** Renders lists of active patient admissions, lab profiles, and immunization records.
* **Auto-Polling:** Regularly polls the backend APIs to fetch and display updates as soon as they are pushed through the integration pipeline.

### Tech Stack
* **React** & **Vite**
* **Vanilla CSS**
* **Development Port:** Typically runs on `http://localhost:5173` or next available port.

### How to Run the Frontend
1. Open a terminal and navigate to the directory:
   ```bash
   cd LangleyChildrensHospital/langley-frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Run the development server:
   ```bash
   npm run dev
   ```
