/**
 * @file PatientCard.jsx
 * @description Card component representing an individual pediatric patient profile.
 * Implements defensive date calculation and BC health identity formatting.
 */

import React from "react";

/**
 * Calculates calendar age in full years from an ISO-8601 date string.
 * @param {string | null} dobString - Birthdate formatted as YYYY-MM-DD.
 * @returns {number | null} Age in years, or null if invalid.
 */
function calculateAge(dobString) {
  if (!dobString) return null;
  const dob = new Date(dobString);
  if (isNaN(dob.getTime())) return null;

  const diffMs = Date.now() - dob.getTime();
  const ageDate = new Date(diffMs);
  return Math.abs(ageDate.getUTCFullYear() - 1970);
}

/**
 * @param {Object} props
 * @param {Object} props.patient - Patient data object from the backend API.
 * @param {(patient: Object) => void} props.onSelect - Callback invoked when user views chart.
 */
export function PatientCard({ patient, onSelect }) {
  const age = calculateAge(patient.dateOfBirth);
  const initials = `${patient.firstName?.[0] || ""}${patient.lastName?.[0] || ""}`.toUpperCase();
  const isAdmitted = Boolean(patient.admitted);

  // Parse comma-delimited allergies into clean array
  const allergiesList = patient.allergies
    ? patient.allergies
        .split(",")
        .map((a) => a.trim())
        .filter(Boolean)
    : [];

  return (
    <article
      className="bcc-patient-card"
      aria-labelledby={`patient-name-${patient.id}`}
    >
      <header className="bcc-card-header">
        <div className="bcc-patient-info">
          <div className="bcc-avatar-circle" aria-hidden="true">
            {initials || "PT"}
          </div>
          <div className="bcc-patient-names">
            <h4 id={`patient-name-${patient.id}`}>
              {patient.lastName}, {patient.firstName}
            </h4>
            <span>
              {age !== null ? `${age} yrs old` : "Age N/A"} • {patient.gender || "Unknown"}
            </span>
          </div>
        </div>

        <span
          className={`bcc-badge ${isAdmitted ? "admitted" : "discharged"}`}
          role="status"
        >
          {isAdmitted ? "● Admitted" : "○ Discharged"}
        </span>
      </header>

      {/* Clinical Identifiers Bar */}
      <div className="bcc-identifiers-bar" aria-label="Patient Identifiers">
        <div className="bcc-id-item" style={{ flex: 1 }}>
          <span className="label">MRN</span>
          <span className="val">{patient.mrn || "N/A"}</span>
        </div>
        <div className="bcc-id-item" style={{ flex: 1.2 }}>
          <span className="label">BC Health Card</span>
          <span className="val" style={{ color: "var(--bcc-cyan)" }}>
            {patient.healthCardNumber || "N/A"}
          </span>
        </div>
        {patient.bloodType && (
          <div className="bcc-id-item">
            <span className="label">Blood</span>
            <span className="val" style={{ color: "var(--bcc-purple)" }}>
              {patient.bloodType}
            </span>
          </div>
        )}
      </div>

      {/* Allergy & Safety Precautions */}
      {allergiesList.length > 0 && (
        <div className="bcc-alerts-container" aria-label="Clinical Alerts">
          <div style={{ display: "flex", flexWrap: "wrap" }}>
            {allergiesList.map((allergy, idx) => (
              <span key={idx} className="bcc-alert-tag">
                Alert: {allergy}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* View Chart Action Button */}
      <button
        className="bcc-view-chart-btn"
        onClick={() => onSelect(patient)}
        aria-label={`Open clinical chart for ${patient.firstName} ${patient.lastName}`}
      >
        <span>View Clinical Chart</span>
        <span aria-hidden="true">&rsaquo;</span>
      </button>
    </article>
  );
}
