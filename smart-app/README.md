# Seymour SMART-on-FHIR Clinical Portal (Angular 17 SPA)

`smart-app` is a modern, standalone **Angular 17** Single-Page Application (SPA) providing a search-driven clinical portal for regional clinicians. It features a SMART-on-FHIR v2.0 authorization launcher, cross-node federated patient discovery, an Enterprise Master Patient Index (EMPI) identity reconciliation engine, and a live emergency RSA key rotation simulator.

---

## 🏛️ Core Features & Components

1. **Search-Driven Clinical Workflow:**
   - On page load, no patient record is auto-populated. The UI presents a clean, glassmorphic search panel.
   - Accepts searches by PHN (`MRN-10001`, `BC9001234567`), MRN, or Patient Name.
   - Includes Quick Selection pills for instant testing (`Margaret Chen`, `Sarah Jenkins`).

2. **Just-In-Time (JIT) OAuth Authentication:**
   - Includes explicit **"⚡ Authenticate SMART OAuth"** button.
   - Executing a search or clicking a quick selection pill when unauthenticated automatically triggers background SMART discovery (`/.well-known/smart-configuration`) and OAuth token acquisition before executing the search query.

3. **Cross-Node Federated Search Algorithm:**
   - First queries primary regional EHR node (`Seymour EHR` on port 8090).
   - If Seymour EHR does not contain the patient record, automatically queries regional specialty node (`Terry Fox Cancer Hospital` on port 8085) and displays the specialty oncology record cleanly.

4. **EMPI Identity Reconciliation Engine:**
   - Evaluates demographic fields across regional health nodes.
   - Detects Date of Birth discrepancies (e.g. 3-day delta: `1948-03-12` vs `1948-03-15`) and name variations.
   - Computes a match confidence score (e.g. `83%`) and displays an amber **EMPI Identity Conflict Warning Banner**.
   - Includes interactive **"🚩 Flag Record for EMPI Audit Review"** button to queue conflict records for human review.

5. **Live Emergency RSA Key Rotation Simulator:**
   - Includes **"🔄 Rotate RSA Keys Live"** header button calling `POST http://localhost:8090/api/admin/rotate-keys`.
   - Triggers live RSA keypair generation in Seymour Auth Server, re-authenticates token, and proves Terry Fox's dynamic `kid` cache eviction in action with zero downtime!

---

## 🧪 Local Execution

```bash
# Install dependencies
npm install

# Start development server
npm start
```

Navigate your browser to **`http://localhost:4200`**.
