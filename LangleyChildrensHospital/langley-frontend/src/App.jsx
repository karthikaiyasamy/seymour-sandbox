/**
 * @file App.jsx
 * @description Main application controller for the Langley Children's Hospital Care Portal.
 * Leverages the custom `usePatients` hook for data synchronization and `useMemo` for derived client-side filtering.
 */

import React, { useState, useMemo } from "react";
import { Header } from "./components/Header";
import { MetricsBar } from "./components/MetricsBar";
import { ControlsPanel } from "./components/ControlsPanel";
import { PatientCard } from "./components/PatientCard";
import { PatientModal } from "./components/PatientModal";
import { usePatients } from "./hooks/usePatients";

export function App() {
  // Custom hook encapsulates polling, AbortController, and network state
  const { patients, isLoading, isConnected, lastSyncTime, error, refresh } = usePatients();

  // Local UI state for search, active filter, and modal selection
  const [searchQuery, setSearchQuery] = useState("");
  const [filterStatus, setFilterStatus] = useState("all");
  const [selectedPatient, setSelectedPatient] = useState(null);

  // Derived memoized counts
  const admittedCount = useMemo(
    () => patients.filter((p) => p.admitted).length,
    [patients]
  );
  const dischargedCount = useMemo(
    () => patients.filter((p) => !p.admitted).length,
    [patients]
  );

  // High-performance real-time search & filter using useMemo
  const filteredPatients = useMemo(() => {
    let result = patients;

    // Filter by admission status
    if (filterStatus === "admitted") {
      result = result.filter((p) => p.admitted);
    } else if (filterStatus === "discharged") {
      result = result.filter((p) => !p.admitted);
    }

    // Filter by search query across multiple clinical fields
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase().trim();
      result = result.filter((p) => {
        const fullName = `${p.firstName || ""} ${p.lastName || ""}`.toLowerCase();
        const mrn = (p.mrn || "").toLowerCase();
        const phn = (p.healthCardNumber || "").toLowerCase();
        const allergies = (p.allergies || "").toLowerCase();
        return (
          fullName.includes(q) ||
          mrn.includes(q) ||
          phn.includes(q) ||
          allergies.includes(q)
        );
      });
    }

    return result;
  }, [patients, filterStatus, searchQuery]);

  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "100vh" }}>
      {/* BC Cancer / PHSA Provincial Header & Navigation Bar */}
      <Header isConnected={isConnected} isPolling={true} />

      {/* Streamlined Clinical Dashboard Top Metrics */}
      <MetricsBar patients={patients} lastSyncTime={lastSyncTime} />

      {/* Main Clinical Roster & Care Management */}
      <main className="bcc-content-wrapper">
        {/* Search, Filter Pills & Data Refresh */}
        <ControlsPanel
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          filterStatus={filterStatus}
          onFilterChange={setFilterStatus}
          onRefresh={refresh}
          isLoading={isLoading}
          totalCount={patients.length}
          admittedCount={admittedCount}
          dischargedCount={dischargedCount}
        />

        {/* Error Notification */}
        {error && (
          <div
            role="alert"
            style={{
              padding: "16px 20px",
              marginBottom: "24px",
              borderRadius: "var(--radius-md)",
              border: "1px solid rgba(220, 38, 38, 0.3)",
              background: "var(--color-danger-bg)",
              color: "var(--color-danger)",
              fontSize: "0.95rem"
            }}
          >
            <strong>Error:</strong> {error}
          </div>
        )}

        {/* Inpatient Roster Section */}
        <section aria-labelledby="roster-heading">
          <div className="bcc-section-title">
            <h3 id="roster-heading">
              Pediatric Inpatient Roster
            </h3>
            <span>
              Showing {filteredPatients.length} of {patients.length} active patient records
            </span>
          </div>

          {filteredPatients.length === 0 ? (
            <div
              style={{
                background: "#ffffff",
                border: "1px solid var(--border-light)",
                borderRadius: "var(--radius-md)",
                padding: "48px 24px",
                textAlign: "center"
              }}
            >
              <div style={{ marginBottom: "8px", color: "var(--bcc-text-muted)" }} aria-hidden="true">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8"></circle>
                  <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                </svg>
              </div>
              <h4 style={{ color: "var(--bcc-navy)", fontSize: "1.1rem" }}>
                {filterStatus !== "all"
                  ? `No patients match the "${filterStatus === "admitted" ? "Admitted" : "Discharged"}" filter`
                  : "No matching patient records"}
              </h4>
              <p
                style={{
                  color: "var(--bcc-text-muted)",
                  fontSize: "0.9rem",
                  marginTop: "4px",
                  marginBottom: "16px"
                }}
              >
                {searchQuery
                  ? `No patients found matching "${searchQuery}". Clear your search or try another keyword.`
                  : `There are currently ${admittedCount} admitted and ${dischargedCount} discharged patient(s).`}
              </p>
              <button
                className="bcc-sync-action-btn"
                style={{ background: "var(--bcc-cyan)", color: "#ffffff", border: "none" }}
                onClick={() => {
                  setFilterStatus("all");
                  setSearchQuery("");
                }}
              >
                Reset to All Roster ({patients.length})
              </button>
            </div>
          ) : (
            <div className="bcc-cards-grid">
              {filteredPatients.map((patient) => (
                <PatientCard
                  key={patient.id}
                  patient={patient}
                  onSelect={setSelectedPatient}
                />
              ))}
            </div>
          )}
        </section>
      </main>

      {/* Clinical Chart Modal Dialog */}
      <PatientModal
        patient={selectedPatient}
        onClose={() => setSelectedPatient(null)}
      />

      {/* Provincial Healthcare Footer */}
      <footer className="bcc-footer">
        <div className="bcc-footer-content">
          <div>
            <h4 style={{ fontSize: "1.05rem", fontWeight: 700, color: "#ffffff", marginBottom: "4px" }}>
              Langley Children's Hospital
            </h4>
            <p style={{ fontSize: "0.85rem", color: "#94a3b8" }}>
              Fraser Health Authority • Provincial Health Services Authority (PHSA) Partner Node
            </p>
          </div>
          <div style={{ fontSize: "0.85rem", color: "#94a3b8" }}>
            EHR Integration: <strong>Cerner Millennium</strong> &amp; <strong>Mirth Connect 4.4</strong>
          </div>
        </div>
        <div className="bcc-footer-disclaimer">
          © 2026 Provincial Health Services Authority (PHSA). All health records and demographic identifiers displayed are synthetic for interoperability validation.
        </div>
      </footer>
    </div>
  );
}
