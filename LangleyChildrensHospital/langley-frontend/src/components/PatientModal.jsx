/**
 * @file PatientModal.jsx
 * @description Accessible clinical chart dialog with locked vertical dimensions
 * and horizontal-only animated tab transitions.
 */

import React, { useState, useEffect } from "react";
import { getPatientVaccinations, getPatientLabs } from "../services/api";

/**
 * @param {Object} props
 * @param {Object | null} props.patient - Currently selected patient object.
 * @param {() => void} props.onClose - Modal close handler.
 */
export function PatientModal({ patient, onClose }) {
  const [activeTab, setActiveTab] = useState("overview");
  const [vaccinations, setVaccinations] = useState([]);
  const [labs, setLabs] = useState([]);
  const [isLoadingDetails, setIsLoadingDetails] = useState(false);

  // Fetch sub-records concurrently when patient opens
  useEffect(() => {
    if (!patient) return;

    const controller = new AbortController();
    setIsLoadingDetails(true);

    Promise.all([
      getPatientVaccinations(patient.id, controller.signal),
      getPatientLabs(patient.id, controller.signal)
    ])
      .then(([vaxData, labData]) => {
        setVaccinations(vaxData || []);
        setLabs(labData || []);
        setIsLoadingDetails(false);
      })
      .catch((err) => {
        if (err.name !== "AbortError") {
          console.error("[PatientModal] Sub-records fetch error:", err);
          setIsLoadingDetails(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, [patient]);

  // Global escape key listener to dismiss modal
  useEffect(() => {
    function handleKeyDown(e) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  if (!patient) return null;

  const initials = `${patient.firstName?.[0] || ""}${patient.lastName?.[0] || ""}`.toUpperCase();
  const allergiesList = patient.allergies
    ? patient.allergies
        .split(",")
        .map((a) => a.trim())
        .filter(Boolean)
    : [];

  return (
    <div
      className="bcc-modal-backdrop"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-patient-name"
    >
      <div
        className="bcc-modal-dialog"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <header className="bcc-modal-header">
          <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
            <div
              className="bcc-avatar-circle"
              style={{ width: "44px", height: "44px", fontSize: "1rem" }}
              aria-hidden="true"
            >
              {initials || "PT"}
            </div>
            <div className="bcc-modal-title">
              <h3 id="modal-patient-name">
                {patient.lastName}, {patient.firstName}
              </h3>
              <p>
                MRN: <strong>{patient.mrn}</strong> • Regional Cerner/Seymour ID: #{patient.seymourPatientId || patient.id}
              </p>
            </div>
          </div>
          <button
            className="bcc-modal-close-btn"
            onClick={onClose}
            aria-label="Close patient dialog"
          >
            &times;
          </button>
        </header>

        {/* Modal Navigation Tabs (Accessible Tablist) */}
        <div className="bcc-modal-tabs" role="tablist" aria-label="Clinical Chart Sections">
          <button
            role="tab"
            id="tab-overview"
            aria-controls="panel-overview"
            aria-selected={activeTab === "overview"}
            className={`bcc-modal-tab-btn ${activeTab === "overview" ? "active" : ""}`}
            onClick={() => setActiveTab("overview")}
          >
            Patient Demographics
          </button>
          <button
            role="tab"
            id="tab-vaccinations"
            aria-controls="panel-vaccinations"
            aria-selected={activeTab === "vaccinations"}
            className={`bcc-modal-tab-btn ${activeTab === "vaccinations" ? "active" : ""}`}
            onClick={() => setActiveTab("vaccinations")}
          >
            Immunizations ({vaccinations.length})
          </button>
          <button
            role="tab"
            id="tab-labs"
            aria-controls="panel-labs"
            aria-selected={activeTab === "labs"}
            className={`bcc-modal-tab-btn ${activeTab === "labs" ? "active" : ""}`}
            onClick={() => setActiveTab("labs")}
          >
            Diagnostic Labs ({labs.length})
          </button>
        </div>

        {/* Modal Body - Fixed 530px container height prevents vertical layout shift */}
        <div className="bcc-modal-body">
          {activeTab === "overview" && (
            <div
              role="tabpanel"
              id="panel-overview"
              aria-labelledby="tab-overview"
              className="bcc-tab-pane"
              key="overview"
            >
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "16px 24px",
                  marginBottom: "24px"
                }}
              >
                <div>
                  <div style={{ fontSize: "0.75rem", textTransform: "uppercase", color: "var(--bcc-text-muted)", fontWeight: 600 }}>
                    Date of Birth
                  </div>
                  <div style={{ fontSize: "0.95rem", fontWeight: 600, color: "var(--bcc-navy)", marginTop: "2px" }}>
                    {patient.dateOfBirth || "N/A"}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.75rem", textTransform: "uppercase", color: "var(--bcc-text-muted)", fontWeight: 600 }}>
                    Administrative Sex
                  </div>
                  <div style={{ fontSize: "0.95rem", fontWeight: 600, color: "var(--bcc-navy)", marginTop: "2px", textTransform: "capitalize" }}>
                    {patient.gender || "N/A"}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.75rem", textTransform: "uppercase", color: "var(--bcc-text-muted)", fontWeight: 600 }}>
                    BC Personal Health Number (PHN)
                  </div>
                  <div style={{ fontSize: "0.95rem", fontWeight: 700, color: "var(--bcc-cyan)", marginTop: "2px" }}>
                    {patient.healthCardNumber || "N/A"}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.75rem", textTransform: "uppercase", color: "var(--bcc-text-muted)", fontWeight: 600 }}>
                    Blood Group
                  </div>
                  <div style={{ fontSize: "0.95rem", fontWeight: 700, color: "var(--bcc-purple)", marginTop: "2px" }}>
                    {patient.bloodType || "N/A"}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.75rem", textTransform: "uppercase", color: "var(--bcc-text-muted)", fontWeight: 600 }}>
                    Emergency Contact Phone
                  </div>
                  <div style={{ fontSize: "0.95rem", color: "var(--bcc-text-dark)", marginTop: "2px" }}>
                    {patient.phone || "N/A"}
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: "0.75rem", textTransform: "uppercase", color: "var(--bcc-text-muted)", fontWeight: 600 }}>
                    Family Email
                  </div>
                  <div style={{ fontSize: "0.95rem", color: "var(--bcc-text-dark)", marginTop: "2px" }}>
                    {patient.email || "N/A"}
                  </div>
                </div>
              </div>

              {/* Allergy Precaution Alerts */}
              <div style={{ borderTop: "1px solid var(--border-light)", paddingTop: "18px" }}>
                <h4 style={{ fontSize: "0.95rem", fontWeight: 700, color: "var(--bcc-navy)", marginBottom: "8px" }}>
                  Clinical Alerts &amp; Allergy Precautions
                </h4>
                {allergiesList.length > 0 ? (
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
                    {allergiesList.map((allergy, idx) => (
                      <span key={idx} className="bcc-alert-tag">
                        Alert: {allergy}
                      </span>
                    ))}
                  </div>
                ) : (
                  <p style={{ color: "var(--bcc-text-muted)", fontSize: "0.88rem" }}>
                    No known drug or environmental allergies on record.
                  </p>
                )}
              </div>
            </div>
          )}

          {activeTab === "vaccinations" && (
            <div
              role="tabpanel"
              id="panel-vaccinations"
              aria-labelledby="tab-vaccinations"
              className="bcc-tab-pane"
              key="vaccinations"
            >
              {isLoadingDetails ? (
                <p style={{ color: "var(--bcc-text-muted)", padding: "16px 0" }}>Loading immunization schedule...</p>
              ) : vaccinations.length === 0 ? (
                <p style={{ color: "var(--bcc-text-muted)", padding: "16px 0" }}>No vaccination events found for this pediatric patient.</p>
              ) : (
                <table className="bcc-data-table">
                  <thead>
                    <tr>
                      <th scope="col">Vaccine Name</th>
                      <th scope="col">CVX Code</th>
                      <th scope="col">Administered Date</th>
                      <th scope="col">Lot #</th>
                      <th scope="col">Clinician</th>
                    </tr>
                  </thead>
                  <tbody>
                    {vaccinations.map((v) => (
                      <tr key={v.id}>
                        <td style={{ fontWeight: 600 }}>{v.vaccineName}</td>
                        <td style={{ fontFamily: "monospace", color: "var(--bcc-text-muted)" }}>{v.vaccineCode}</td>
                        <td>{v.administrationDate ? new Date(v.administrationDate).toLocaleDateString() : "N/A"}</td>
                        <td style={{ fontFamily: "monospace", color: "var(--bcc-cyan)" }}>{v.lotNumber || "N/A"}</td>
                        <td>{v.administeredBy || "Clinical Staff"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}

          {activeTab === "labs" && (
            <div
              role="tabpanel"
              id="panel-labs"
              aria-labelledby="tab-labs"
              className="bcc-tab-pane"
              key="labs"
            >
              {isLoadingDetails ? (
                <p style={{ color: "var(--bcc-text-muted)", padding: "16px 0" }}>Loading laboratory test results...</p>
              ) : labs.length === 0 ? (
                <p style={{ color: "var(--bcc-text-muted)", padding: "16px 0" }}>No laboratory test panels recorded for this patient.</p>
              ) : (
                <table className="bcc-data-table">
                  <thead>
                    <tr>
                      <th scope="col">Test Description</th>
                      <th scope="col">LOINC Code</th>
                      <th scope="col">Result</th>
                      <th scope="col">Units</th>
                      <th scope="col">Clinical Flag</th>
                      <th scope="col">Reported</th>
                    </tr>
                  </thead>
                  <tbody>
                    {labs.map((l) => (
                      <tr key={l.id}>
                        <td style={{ fontWeight: 600 }}>{l.testName}</td>
                        <td style={{ fontFamily: "monospace", color: "var(--bcc-text-muted)" }}>{l.testCode}</td>
                        <td style={{ fontWeight: 700, color: "var(--bcc-cyan)" }}>{l.resultValue}</td>
                        <td>{l.unit}</td>
                        <td>
                          <span
                            className="bcc-badge"
                            style={{
                              backgroundColor: l.flag === "N" ? "var(--color-success-bg)" : "var(--color-danger-bg)",
                              color: l.flag === "N" ? "var(--color-success)" : "var(--color-danger)"
                            }}
                          >
                            {l.flag === "N" ? "NORMAL" : l.flag || "ABNORMAL"}
                          </span>
                        </td>
                        <td style={{ fontSize: "0.85rem", color: "var(--bcc-text-muted)" }}>
                          {l.testDate ? new Date(l.testDate).toLocaleDateString() : "N/A"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
