/**
 * @file ControlsPanel.jsx
 * @description Search input, admission status filter tabs, and manual synchronization trigger.
 * Demonstrates the Controlled Component pattern and accessible button states.
 */

import React from "react";

/**
 * @param {Object} props
 * @param {string} props.searchQuery - Current search input string.
 * @param {(query: string) => void} props.onSearchChange - Search input change handler.
 * @param {"all" | "admitted" | "discharged"} props.filterStatus - Active filter selection.
 * @param {(status: "all" | "admitted" | "discharged") => void} props.onFilterChange - Filter selection handler.
 * @param {() => void} props.onRefresh - Manual refresh callback.
 * @param {boolean} props.isLoading - Whether a manual sync is currently in progress.
 * @param {number} props.totalCount - Total number of patients in the database.
 * @param {number} props.admittedCount - Number of admitted patients.
 * @param {number} props.dischargedCount - Number of discharged patients.
 */
export function ControlsPanel({
  searchQuery,
  onSearchChange,
  filterStatus,
  onFilterChange,
  onRefresh,
  isLoading,
  totalCount = 0,
  admittedCount = 0,
  dischargedCount = 0
}) {
  return (
    <section className="bcc-controls-card" aria-label="Roster Controls and Filters">
      {/* Search Bar - Controlled Component */}
      <div className="bcc-search-pill" role="search">
        <input
          type="text"
          placeholder="Search by child name, MRN, or BC Health Card (PHN)..."
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          aria-label="Search patients"
        />
        {searchQuery && (
          <button
            onClick={() => onSearchChange("")}
            style={{
              background: "none",
              border: "none",
              color: "var(--bcc-text-muted)",
              cursor: "pointer",
              padding: "0 6px",
              fontSize: "1rem"
            }}
            aria-label="Clear search input"
            title="Clear search"
          >
            ✕
          </button>
        )}
        <button className="bcc-search-icon-btn" aria-label="Execute search" tabIndex={-1}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <circle cx="11" cy="11" r="8"></circle>
            <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
          </svg>
        </button>
      </div>

      {/* Filter Tabs Group */}
      <div className="bcc-filter-group" role="group" aria-label="Admission Status Filter">
        <button
          className={`bcc-filter-btn ${filterStatus === "all" ? "active" : ""}`}
          onClick={() => onFilterChange("all")}
          aria-pressed={filterStatus === "all"}
        >
          All Roster ({totalCount})
        </button>
        <button
          className={`bcc-filter-btn ${filterStatus === "admitted" ? "active" : ""}`}
          onClick={() => onFilterChange("admitted")}
          aria-pressed={filterStatus === "admitted"}
        >
          Admitted ({admittedCount})
        </button>
        <button
          className={`bcc-filter-btn ${filterStatus === "discharged" ? "active" : ""}`}
          onClick={() => onFilterChange("discharged")}
          aria-pressed={filterStatus === "discharged"}
        >
          Discharged ({dischargedCount})
        </button>
      </div>

      {/* Manual Sync Action */}
      <button
        className="bcc-sync-action-btn"
        onClick={onRefresh}
        disabled={isLoading}
        aria-busy={isLoading}
      >
        <span>{isLoading ? "Syncing..." : "Refresh Data"}</span>
      </button>
    </section>
  );
}
