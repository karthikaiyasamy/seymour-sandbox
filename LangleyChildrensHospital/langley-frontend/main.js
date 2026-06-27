// Langley Children's Hospital Patient Synchronization Console Controller
const BACKEND_URL = "http://localhost:8081/api/patients";

// App State
let patients = [];
let knownPatientIds = new Set();
let selectedPatient = null;
let currentFilterStatus = "all";
let currentSearchQuery = "";
let pollInterval = null;

// DOM Elements
const connectionIndicator = document.getElementById("connection-indicator");
const connectionLabel = document.getElementById("connection-label");
const metricTotal = document.getElementById("metric-total");
const metricAdmitted = document.getElementById("metric-admitted");
const metricDischarged = document.getElementById("metric-discharged");
const metricLastSync = document.getElementById("metric-last-sync");
const searchInput = document.getElementById("search-input");
const filterButtons = document.querySelectorAll(".controls-panel .filter-btn");
const refreshButton = document.getElementById("refresh-button");
const rosterCount = document.getElementById("roster-count");
const loadingState = document.getElementById("loading-state");
const emptyState = document.getElementById("empty-state");
const patientsGrid = document.getElementById("patients-grid");

// Modal Elements
const detailModal = document.getElementById("detail-modal");
const modalClose = document.getElementById("modal-close");
const modalAvatar = document.getElementById("modal-avatar");
const modalName = document.getElementById("modal-name");
const modalStatusBadge = document.getElementById("modal-status-badge");
const modalMrn = document.getElementById("modal-mrn");
const modalSeymourId = document.getElementById("modal-seymour-id");
const modalDob = document.getElementById("modal-dob");
const modalGender = document.getElementById("modal-gender");
const modalPhone = document.getElementById("modal-phone");
const modalEmail = document.getElementById("modal-email");
const modalHealthCard = document.getElementById("modal-healthcard");
const modalBloodType = document.getElementById("modal-bloodtype");
const modalAllergies = document.getElementById("modal-allergies");
const modalSyncedAt = document.getElementById("modal-synced-at");

// Initialize application
document.addEventListener("DOMContentLoaded", () => {
  setupEventListeners();
  fetchPatients(true); // Initial load with spinner
  startPolling();
});

// Event Listeners setup
function setupEventListeners() {
  // Search input change
  searchInput.addEventListener("input", (e) => {
    currentSearchQuery = e.target.value.toLowerCase().trim();
    renderRoster();
  });

  // Filter tabs
  filterButtons.forEach(btn => {
    btn.addEventListener("click", () => {
      filterButtons.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      currentFilterStatus = btn.dataset.status;
      renderRoster();
    });
  });

  // Sync button click
  refreshButton.addEventListener("click", () => {
    fetchPatients(false);
  });

  // Close modal when clicking X
  modalClose.addEventListener("click", closeModal);

  // Tab switching inside detail modal
  document.getElementById("modal-tab-vaccines-btn").addEventListener("click", () => switchModalTab("vaccines"));
  document.getElementById("modal-tab-labs-btn").addEventListener("click", () => switchModalTab("labs"));

  // Close modal when clicking outside container
  detailModal.addEventListener("click", (e) => {
    if (e.target === detailModal) {
      closeModal();
    }
  });

  // Keyboard close support
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && !detailModal.classList.contains("hidden")) {
      closeModal();
    }
  });
}

// Fetch Patients list from Langley Backend
async function fetchPatients(showSpinner = false) {
  if (showSpinner) {
    loadingState.classList.remove("hidden");
    patientsGrid.classList.add("hidden");
    emptyState.classList.add("hidden");
  }
  
  refreshButton.classList.add("spinning");

  try {
    const response = await fetch(BACKEND_URL);
    if (!response.ok) throw new Error(`HTTP Error ${response.status}`);
    
    const data = await response.json();
    updateConnectionStatus(true);
    
    // Sort patients so newest additions are listed first or sorted by name
    data.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    
    processNewPatients(data);
    patients = data;
    
    updateMetrics();
    renderRoster();
    
  } catch (error) {
    console.error("Failed to fetch patients from Langley backend:", error);
    updateConnectionStatus(false, error.message);
  } finally {
    if (showSpinner) {
      loadingState.classList.add("hidden");
    }
    refreshButton.classList.remove("spinning");
  }
}

// Highlight and process newly synced entries
function processNewPatients(newData) {
  const newIds = new Set(newData.map(p => p.id));
  
  // Track if this is a subsequent load
  if (knownPatientIds.size > 0) {
    newData.forEach(p => {
      if (!knownPatientIds.has(p.id)) {
        // Tag as new entry for visual highlight
        p.isNew = true;
        // Auto remove the flag after animation finishes
        setTimeout(() => {
          p.isNew = false;
          const element = document.getElementById(`patient-card-${p.id}`);
          if (element) element.classList.remove("new-entry");
        }, 5000);
      }
    });
  }
  
  knownPatientIds = newIds;
}

// Update network status display
function updateConnectionStatus(isConnected, message = "") {
  const timestamp = new Date().toLocaleTimeString();
  metricLastSync.textContent = timestamp;

  if (isConnected) {
    connectionIndicator.className = "status-indicator connected";
    connectionLabel.textContent = "Synced with Langley Backend";
  } else {
    connectionIndicator.className = "status-indicator disconnected";
    connectionLabel.textContent = `Offline: ${message || "Cannot reach backend"}`;
  }
}

// Calculate and update KPIs
function updateMetrics() {
  const total = patients.length;
  const admitted = patients.filter(p => p.admitted).length;
  const discharged = total - admitted;

  metricTotal.textContent = total;
  metricAdmitted.textContent = admitted;
  metricDischarged.textContent = discharged;
}

// Filter and render patients list
function renderRoster() {
  // Apply Search and Status Filters
  const filtered = patients.filter(p => {
    const matchesSearch = 
      (p.firstName || "").toLowerCase().includes(currentSearchQuery) ||
      (p.lastName || "").toLowerCase().includes(currentSearchQuery) ||
      (p.mrn || "").toLowerCase().includes(currentSearchQuery) ||
      String(p.seymourPatientId || "").toLowerCase().includes(currentSearchQuery);

    const matchesStatus = 
      currentFilterStatus === "all" ||
      (currentFilterStatus === "admitted" && p.admitted) ||
      (currentFilterStatus === "discharged" && !p.admitted);

    return matchesSearch && matchesStatus;
  });

  rosterCount.textContent = `${filtered.length} patient${filtered.length === 1 ? "" : "s"} listed`;

  if (patients.length === 0) {
    emptyState.classList.remove("hidden");
    patientsGrid.classList.add("hidden");
    return;
  }

  emptyState.classList.add("hidden");
  patientsGrid.classList.remove("hidden");
  patientsGrid.innerHTML = "";

  if (filtered.length === 0) {
    patientsGrid.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1; padding: 3rem;">
        <div class="empty-icon">🔍</div>
        <h3>No matches found</h3>
        <p>Try refining your search terms or status filters.</p>
      </div>
    `;
    return;
  }

  filtered.forEach(p => {
    const card = document.createElement("div");
    card.id = `patient-card-${p.id}`;
    card.className = `patient-card glass ${p.admitted ? "admitted-status" : "discharged-status"}`;
    if (p.isNew) card.classList.add("new-entry");

    const initials = `${p.firstName.charAt(0) || ""}${p.lastName.charAt(0) || ""}`.toUpperCase();
    const age = calculateAge(p.dateOfBirth);
    const genderCap = capitalize(p.gender);
    const statusLabel = p.admitted ? "Admitted" : "Discharged";

    card.innerHTML = `
      <div class="card-header">
        <div class="patient-identity">
          <h3>${p.firstName} ${p.lastName}</h3>
          <span class="patient-mrn">${p.mrn}</span>
        </div>
        <span class="status-badge ${p.admitted ? "admitted" : "discharged"}">${statusLabel}</span>
      </div>
      <div class="card-details">
        <div class="detail-row">
          <span class="detail-label">Age / Sex</span>
          <span class="detail-value">${age ? `${age}y` : "N/A"} / ${genderCap}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">Seymour ID</span>
          <span class="detail-value">#${p.seymourPatientId}</span>
        </div>
      </div>
      <div class="card-clinical">
        <span class="clinical-badge blood">Blood: ${p.bloodType || "Unknown"}</span>
        <span class="clinical-badge allergy" title="${p.allergies}">Allergies: ${p.allergies || "None"}</span>
      </div>
      <div class="card-footer">
        <span class="sync-badge">📡 Synced</span>
        <span>${formatDate(p.createdAt)}</span>
      </div>
    `;

    // Click handler to open details
    card.addEventListener("click", () => showPatientDetails(p));

    patientsGrid.appendChild(card);
  });
}

// Show modal with patient details
async function showPatientDetails(patient) {
  selectedPatient = patient;
  
  const initials = `${patient.firstName.charAt(0) || ""}${patient.lastName.charAt(0) || ""}`.toUpperCase();
  modalAvatar.textContent = initials;
  modalName.textContent = `${patient.firstName} ${patient.lastName}`;
  
  // Update status badge
  modalStatusBadge.textContent = patient.admitted ? "Admitted" : "Discharged";
  modalStatusBadge.className = `badge status-badge ${patient.admitted ? "admitted" : "discharged"}`;
  
  modalMrn.textContent = patient.mrn;
  modalSeymourId.textContent = patient.seymourPatientId;
  
  const age = calculateAge(patient.dateOfBirth);
  modalDob.textContent = patient.dateOfBirth 
    ? `${patient.dateOfBirth} (${age} years old)` 
    : "N/A";
  modalGender.textContent = capitalize(patient.gender);
  
  modalPhone.textContent = patient.phone || "No phone record";
  modalEmail.textContent = patient.email || "No email record";
  modalHealthCard.textContent = patient.healthCardNumber || "N/A";
  modalBloodType.textContent = patient.bloodType || "Unknown";
  modalAllergies.textContent = patient.allergies || "NKDA (No Known Drug Allergies)";
  
  modalSyncedAt.textContent = `Synchronized at: ${new Date(patient.createdAt).toLocaleString()}`;
  
  // Reset clinical history tabs
  switchModalTab('vaccines');

  // Load Vaccines and Labs
  await Promise.all([
    loadPatientVaccines(patient.id),
    loadPatientLabs(patient.id)
  ]);

  detailModal.classList.remove("hidden");
}

function switchModalTab(tab) {
  const vacBtn = document.getElementById("modal-tab-vaccines-btn");
  const labBtn = document.getElementById("modal-tab-labs-btn");
  const vacPanel = document.getElementById("modal-vaccines-panel");
  const labPanel = document.getElementById("modal-labs-panel");

  if (tab === 'vaccines') {
    vacBtn.classList.add("active");
    vacBtn.style.color = "var(--accent-cyan)";
    vacBtn.style.background = "rgba(6, 182, 212, 0.15)";
    vacBtn.style.borderColor = "rgba(6, 182, 212, 0.3)";

    labBtn.classList.remove("active");
    labBtn.style.color = "var(--text-secondary)";
    labBtn.style.background = "rgba(255, 255, 255, 0.05)";
    labBtn.style.borderColor = "var(--card-border)";

    vacPanel.style.display = "block";
    labPanel.style.display = "none";
  } else {
    labBtn.classList.add("active");
    labBtn.style.color = "var(--accent-cyan)";
    labBtn.style.background = "rgba(6, 182, 212, 0.15)";
    labBtn.style.borderColor = "rgba(6, 182, 212, 0.3)";

    vacBtn.classList.remove("active");
    vacBtn.style.color = "var(--text-secondary)";
    vacBtn.style.background = "rgba(255, 255, 255, 0.05)";
    vacBtn.style.borderColor = "var(--card-border)";

    vacPanel.style.display = "none";
    labPanel.style.display = "block";
  }
}

async function loadPatientVaccines(patientId) {
  const tbody = document.getElementById("modal-vaccines-tbody");
  tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; padding: 1rem;">Loading...</td></tr>`;

  try {
    const res = await fetch(`http://localhost:8081/api/patients/${patientId}/vaccinations`);
    if (!res.ok) throw new Error("Failed to load vaccinations");
    const data = await res.json();

    if (data.length === 0) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; padding: 1rem;">No vaccination records synced.</td></tr>`;
      return;
    }

    tbody.innerHTML = data.map(v => {
      const dateStr = v.administrationDate ? new Date(v.administrationDate).toLocaleString() : "N/A";
      return `
        <tr>
          <td style="padding: 0.5rem; font-weight: 500; color: var(--text-primary);">${v.vaccineName || 'Unknown'}</td>
          <td style="padding: 0.5rem;"><code>${v.vaccineCode || 'N/A'}</code></td>
          <td style="padding: 0.5rem;">${dateStr}</td>
          <td style="padding: 0.5rem;"><code>${v.lotNumber || 'N/A'}</code></td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    console.error(err);
    tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; padding: 1rem; color: var(--accent-red);">Error loading vaccines.</td></tr>`;
  }
}

async function loadPatientLabs(patientId) {
  const tbody = document.getElementById("modal-labs-tbody");
  tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; padding: 1rem;">Loading...</td></tr>`;

  try {
    const res = await fetch(`http://localhost:8081/api/patients/${patientId}/labs`);
    if (!res.ok) throw new Error("Failed to load lab results");
    const data = await res.json();

    if (data.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; padding: 1rem;">No lab records synced.</td></tr>`;
      return;
    }

    tbody.innerHTML = data.map(l => {
      const dateStr = l.testDate ? new Date(l.testDate).toLocaleString() : "N/A";
      const isAbnormal = l.flag && l.flag !== 'N' && l.flag !== 'Normal';
      const flagClass = isAbnormal ? 'badge discharged' : 'badge admitted'; // standard colors in styling
      const flagText = l.flag === 'N' ? 'Normal' : (l.flag === 'A' ? 'Abnormal' : l.flag);
      return `
        <tr>
          <td style="padding: 0.5rem; font-weight: 500; color: var(--text-primary);">${l.testName || 'Unknown'}</td>
          <td style="padding: 0.5rem;"><code>${l.testCode || 'N/A'}</code></td>
          <td style="padding: 0.5rem;"><strong>${l.resultValue || ''}</strong> ${l.unit || ''}</td>
          <td style="padding: 0.5rem;"><span class="${flagClass}">${flagText}</span></td>
          <td style="padding: 0.5rem;">${dateStr}</td>
        </tr>
      `;
    }).join('');
  } catch (err) {
    console.error(err);
    tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; padding: 1rem; color: var(--accent-red);">Error loading labs.</td></tr>`;
  }
}

function closeModal() {
  detailModal.classList.add("hidden");
  selectedPatient = null;
}

// Background auto-polling
function startPolling() {
  if (pollInterval) clearInterval(pollInterval);
  pollInterval = setInterval(() => {
    fetchPatients(false); // poll silently without loading spinner
  }, 5000);
}

// Helper: Calculate age from date string
function calculateAge(dobString) {
  if (!dobString) return null;
  // Robust parsing of YYYY-MM-DD format (cross-browser compatible)
  const parts = dobString.split('-');
  if (parts.length !== 3) return null;
  
  const birthYear = parseInt(parts[0], 10);
  const birthMonth = parseInt(parts[1], 10) - 1; // months are 0-indexed
  const birthDay = parseInt(parts[2], 10);
  
  const today = new Date();
  let age = today.getFullYear() - birthYear;
  const monthDiff = today.getMonth() - birthMonth;
  
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birthDay)) {
    age--;
  }
  return isNaN(age) ? null : age;
}

// Helper: Capitalize word
function capitalize(word) {
  if (!word) return "Unknown";
  return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
}

// Helper: Format ISO date to readable HH:MM
function formatDate(dateString) {
  if (!dateString) return "";
  try {
    const d = new Date(dateString);
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch (e) {
    return dateString;
  }
}
