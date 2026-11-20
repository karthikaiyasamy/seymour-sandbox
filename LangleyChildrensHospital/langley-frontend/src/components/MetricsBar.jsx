/**
 * @file MetricsBar.jsx
 * @description Dashboard metric KPI summary tiles (Total, Admitted, Discharged, Polling Time).
 */

import React from "react";

/**
 * @param {Object} props
 * @param {Array<Object>} props.patients - Full patient array.
 * @param {Date | null} props.lastSyncTime - Timestamp of the last successful synchronization.
 */
export function MetricsBar({ patients = [], lastSyncTime }) {
  const total = patients.length;
  const admitted = patients.filter((p) => p.admitted).length;
  const discharged = total - admitted;

  const formattedTime = lastSyncTime
    ? new Date(lastSyncTime).toLocaleTimeString([], {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit"
      })
    : "--:--:--";

  return (
    <section className="bcc-metrics-container" aria-label="Clinical Metrics Overview">
      <div className="bcc-metric-tile">
        <div className="bcc-metric-icon" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="9" cy="7" r="4"></circle>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
            <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
          </svg>
        </div>
        <div className="bcc-metric-data">
          <h4>{total}</h4>
          <p>Active Inpatients</p>
        </div>
      </div>

      <div className="bcc-metric-tile green-accent">
        <div className="bcc-metric-icon" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M22 12h-4l-3 9L9 3l-3 9H2"></path>
          </svg>
        </div>
        <div className="bcc-metric-data">
          <h4 style={{ color: "var(--color-success)" }}>{admitted}</h4>
          <p>Admitted Beds</p>
        </div>
      </div>

      <div className="bcc-metric-tile purple-accent">
        <div className="bcc-metric-icon" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path>
            <polyline points="9 22 9 12 15 12 15 22"></polyline>
          </svg>
        </div>
        <div className="bcc-metric-data">
          <h4>{discharged}</h4>
          <p>Outpatient / Discharged</p>
        </div>
      </div>

      <div className="bcc-metric-tile">
        <div className="bcc-metric-icon" aria-hidden="true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
          </svg>
        </div>
        <div className="bcc-metric-data">
          <h4 style={{ fontSize: "1.35rem" }}>{formattedTime}</h4>
          <p>Last Mirth Poll</p>
        </div>
      </div>
    </section>
  );
}
