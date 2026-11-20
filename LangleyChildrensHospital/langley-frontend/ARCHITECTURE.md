# Langley Children's Hospital — React Architecture Guide
### *A Mental Model & Architectural Bridge for Java & Systems Engineers*

This document breaks down the architecture, lifecycle, and design patterns of the **Langley Children's Hospital Care Portal** frontend. It is written specifically for engineers with a strong backend (Java / Spring Boot / SQL) and HTML/JS background who are mastering modern React.

---

## 1. The Java-to-React "Rosetta Stone"

If you come from Java and Spring Boot, React makes complete sense once you map its concepts to familiar backend patterns:

| Java / Spring Boot Concept | Vanilla JS Concept | React Equivalent | Why React Does It This Way |
| :--- | :--- | :--- | :--- |
| **Java Record / DTO / Method Parameters** | Object passed to a function | **`props`** | Read-only input data passed into a component. React enforces immutability (`final`). |
| **Private field with a Setter** (`private int count; setCount(val)`) | Global/module variable | **`useState`** | You cannot just do `this.count = 5`. Calling `setCount(5)` signals React to re-render the view. |
| **`@PostConstruct`** (Runs on startup) | `window.onload` or `DOMContentLoaded` | **`useEffect(() => {...}, [])`** | Empty dependency array `[]` means: run once when the component is mounted to the screen. |
| **`@PreDestroy` / Resource Cleanup** | `window.removeEventListener()` | **`return () => clearInterval(...)`** | The function returned by `useEffect` runs when the component is destroyed (unmounted). |
| **Spring `@Service` + `@Scheduled`** | Background setInterval loop | **Custom Hook (`usePatients.js`)** | Encapsulates background polling, state storage, and error tracking away from the UI. |
| **Guava `@Cacheable` / Memoization** | Caching variable in a Map | **`useMemo`** | Computes a derived value (like search filtering) only when underlying dependencies change. |
| **Method Reference** (`this::handleSelect`) | Function pointer | **`useCallback`** | Prevents creating a new function in memory every render cycle, avoiding child re-renders. |
| **Spring `RestTemplate` / `WebClient`** | Raw `fetch()` or `XMLHttpRequest` | **`src/services/api.js`** | Pure HTTP boundary layer. No UI awareness. |
| **JSP / Thymeleaf / Mustache templates** | String interpolation into `.innerHTML` | **JSX (`.jsx`)** | Syntactic sugar for `React.createElement()`. Allows writing HTML-like tags directly in JavaScript. |

---

## 2. The Core Mental Model Shift: Imperative vs. Declarative

### The Old Imperative Way (Vanilla JS / Java Swing / DOM Mutation)
In Java Swing or Vanilla JS, you manually manipulate elements step-by-step:
```javascript
// Imperative (You micromanage the DOM)
const button = document.getElementById("sync-btn");
button.innerText = "Syncing...";
button.disabled = true;

const roster = document.getElementById("roster");
roster.innerHTML = ""; // Clear existing table
patients.forEach(p => {
  const tr = document.createElement("tr");
  tr.innerText = p.name;
  roster.appendChild(tr);
});
```

### The Modern Declarative Way (React)
In React, **you never touch the DOM directly**. You simply describe what the UI *should look like* for a given state:

$$\text{UI} = f(\text{State})$$

```jsx
// Declarative (React manages the DOM for you)
function PatientList({ patients, isLoading }) {
  return (
    <div>
      <button disabled={isLoading}>
        {isLoading ? "Syncing..." : "Manual Sync"}
      </button>

      {patients.map(p => (
        <PatientCard key={p.id} patient={p} />
      ))}
    </div>
  );
}
```
When `isLoading` or `patients` changes, React re-runs the function, calculates the minimal diff in the **Virtual DOM**, and updates only the changed pixels on screen.

---

## 3. High-Level System Flowchart

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                      SPRING BOOT REST API (Port 8081)                  │
 │                      Database: PostgreSQL (langley_db)                 │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ HTTP GET /api/patients (JSON)
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                      src/services/api.js                               │
 │  PURPOSE: Network Transport Layer (Analogue: Spring RestTemplate)      │
 │  - Executes fetch() calls.                                             │
 │  - Supports AbortController to cancel dead in-flight HTTP requests.    │
 │  - Contains NO React code; pure async JavaScript.                      │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ Raw Data Promises
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                    src/hooks/usePatients.js                            │
 │  PURPOSE: Reactive Service Layer (Analogue: Spring @Service)           │
 │  - Manages background polling timer (every 8 seconds).                 │
 │  - Stores reactive state (patients[], isConnected, error).             │
 │  - Handles cleanup when unmounted (clears interval & aborts requests).│
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ React State Object
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                           src/App.jsx                                  │
 │  PURPOSE: Root Layout & Orchestrator (Analogue: Spring @Controller)   │
 │  - Consumes usePatients().                                             │
 │  - Coordinates client-side search & filtering in memory (useMemo).     │
 │  - Manages modal visibility state.                                     │
 └─────────────────┬─────────────────┬──────────────────┬─────────────────┘
                   │                 │                  │                 │
                   │ (props)         │ (props)          │ (props)         │ (props)
                   ▼                 ▼                  ▼                 ▼
             [Header.jsx]     [MetricsBar.jsx]   [ControlsPanel]   [PatientModal]
                   │                                    │                 ▲
                   │                                    │ (map)           │ (onSelect)
                   ▼                                    ▼                 │
             [Top Banners]                        [PatientCard] ──────────┘
```

---

## 4. Architectural Layers & File Breakdown

### Tier 1: Configuration & Infrastructure

#### `.env` & `src/config/appConfig.js`
* **Java Equivalent:** `application.properties` / `@ConfigurationProperties`
* **Purpose:** Externalizes runtime parameters (`VITE_API_BASE_URL`, `VITE_POLLING_INTERVAL_MS`).
* **Why Vite Requires `VITE_`:** Unlike Java server apps where properties stay safely on the server, React code executes inside the client's public web browser. Vite refuses to bundle any `.env` variable into client code unless it starts with `VITE_`, preventing accidental leaks of backend database passwords.

#### `index.html` & `src/main.jsx`
* **Java Equivalent:** `public static void main(String[] args)`
* **Purpose:** Single-Page Application (SPA) entry point. `index.html` contains `<div id="root"></div>`. `main.jsx` invokes `ReactDOM.createRoot()`, compiling the React virtual tree and injecting it into that single `div`.

---

### Tier 2: The Service & Reactive State Layer

#### `src/services/api.js`
* **Java Equivalent:** `PatientClient.java` (Feign Client or Spring `RestClient`)
* **Purpose:** Pure HTTP transport. Formats requests, attaches headers (`Accept: application/json`), and translates HTTP error codes into clean JavaScript `ApiError` exceptions.

#### `src/hooks/usePatients.js`
* **Java Equivalent:** `@Service @Scope("singleton")` managing scheduled background jobs
* **Purpose:** The reactive data engine. 
* **Why it's a "Custom Hook":** In React, standard JS functions cannot cause the screen to re-render. Only React hooks (`useState`) can. By wrapping data fetching in a custom hook, any component that calls `usePatients()` automatically gets fresh data and re-renders whenever the backend changes.
* **Senior Best Practice (AbortController):**
  When a user triggers rapid refreshes or navigates away, older in-flight HTTP requests can finish *after* newer ones, causing race conditions. `usePatients` stores an `AbortController` in a `useRef` to cancel stale in-flight network requests before starting a new one.

---

### Tier 3: The Presentation (UI) Layer

#### `src/App.jsx`
* **Java Equivalent:** Main View Controller orchestrating child views.
* **Key Pattern (`useMemo` for Filtering):**
  Instead of hitting the Spring Boot backend with a new SQL query every time the user types a letter in the search box, `App.jsx` stores the patient roster in memory and uses `useMemo` to filter names, MRNs, and PHNs instantly ($< 1\text{ ms}$).

#### `src/components/ControlsPanel.jsx`
* **Key Pattern (Controlled Component):**
  In old HTML, an `<input>` maintained its own internal text. In React, the input is "controlled"—its value is forced by React state (`value={searchQuery}`) and every keystroke delegates up to React (`onChange`).

#### `src/components/PatientCard.jsx`
* **Java Equivalent:** A presentation template for a `PatientDTO`.
* **Purpose:** Renders demographics, BC Health Card (PHN) chips, blood groups, and formatted allergy alert badges. Dynamically calculates age from `dateOfBirth` without mutating backend objects.

#### `src/components/PatientModal.jsx`
* **Purpose:** Full clinical chart dialog.
* **Key Patterns:**
  1. **Sub-resource Loading:** When opened, fires `Promise.all([getVaccinations(), getLabs()])` to load child entities concurrently.
  2. **Fixed Height Geometry (`530px`):** To avoid "visual layout jitter", the modal dialog maintains a locked height so switching between a short tab (1 vaccine) and a tall tab (demographics) never causes the window to bounce up and down.
  3. **WAI-ARIA Accessibility:** Implements `role="dialog"`, `role="tablist"`, and an `Escape` key event listener.

---

## 5. Critical Gotchas for Java Developers Learning React

### 1. Immutability: Never Mutate State Directly!
* **In Java:** You often do `patientList.add(newPatient);`
* **In React (BUG!):** `patients.push(newPatient); setPatients(patients);` **will not update the screen!**  
  React uses **shallow reference equality** (`prev === next`) to check if data changed. If the array memory pointer is the same, React skips re-rendering.
* **The Correct Way:** Always produce a new copy:
  ```javascript
  setPatients([...patients, newPatient]); // Creates a new array reference
  ```

### 2. State Updates are Asynchronous & Batched
* **In Java:** If you do `this.count = 5; System.out.println(this.count);`, it prints `5`.
* **In React:** Calling `setCount(5)` does *not* immediately update the `count` variable on the next line. It schedules a re-render for the next frame.

### 3. Every Render Re-executes the Whole Function
* In Java, a class method runs once when called.
* In React, every time state updates, the component function (e.g. `App()`) **executes again from line 1 to the end**.
* That is why we use:
  * **`useState`** to keep values alive across re-executions.
  * **`useRef`** to store objects across re-executions without triggering a new render.
  * **`useMemo`** to avoid re-calculating heavy logic on every re-execution.

---

## 6. Suggested Code Reading Order

To build an intuitive grasp of the codebase, inspect the files in this sequence:

1. [src/config/appConfig.js](file:///Users/karthik/dev/cerner/seymour-sandbox/LangleyChildrensHospital/langley-frontend/src/config/appConfig.js): See how properties are loaded.
2. [src/services/api.js](file:///Users/karthik/dev/cerner/seymour-sandbox/LangleyChildrensHospital/langley-frontend/src/services/api.js): See how standard HTTP calls are formatted.
3. [src/hooks/usePatients.js](file:///Users/karthik/dev/cerner/seymour-sandbox/LangleyChildrensHospital/langley-frontend/src/hooks/usePatients.js): See how React wraps HTTP calls into reactive state with polling.
4. [src/App.jsx](file:///Users/karthik/dev/cerner/seymour-sandbox/LangleyChildrensHospital/langley-frontend/src/App.jsx): See how the root component connects the custom hook to the UI.
5. [src/components/PatientCard.jsx](file:///Users/karthik/dev/cerner/seymour-sandbox/LangleyChildrensHospital/langley-frontend/src/components/PatientCard.jsx) & [PatientModal.jsx](file:///Users/karthik/dev/cerner/seymour-sandbox/LangleyChildrensHospital/langley-frontend/src/components/PatientModal.jsx): Inspect modular presentation and dialog logic.
