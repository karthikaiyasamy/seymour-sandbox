/**
 * @file Header.jsx
 * @description Provincial healthcare navigation header with educational notice and live connection status.
 */

import React from "react";

/**
 * @param {Object} props
 * @param {boolean} props.isConnected - Whether backend is reachable.
 * @param {boolean} props.isPolling - Whether auto-polling is active.
 */
export function Header({ isConnected, isPolling }) {
  return (
    <header role="banner">
      {/* Synthetic Learning Sandbox Notice Banner */}
      <aside
        className="bcc-notice-bar"
        aria-label="Sandbox notification"
        style={{
          background: "linear-gradient(90deg, #e0f2fe 0%, #ede9fe 100%)",
          borderBottom: "1px solid #bae6fd",
          color: "#0369a1",
          padding: "6px 24px",
          fontSize: "0.82rem",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: "10px"
        }}
      >
        <span
          style={{
            background: "#0284c7",
            color: "#ffffff",
            padding: "2px 8px",
            borderRadius: "4px",
            fontSize: "0.72rem",
            fontWeight: 700,
            letterSpacing: "0.5px"
          }}
        >
          DEMO / LEARNING SANDBOX
        </span>
        <span style={{ color: "#0f172a", fontWeight: 500 }}>
          All patient profiles, PHNs, and clinical identifiers are <strong>100% synthetic</strong> for learning <strong>FHIR R4</strong>, <strong>React.js</strong>, and <strong>BC Health Authority Interoperability</strong>.
        </span>
      </aside>

      {/* BC Provincial Utility Topbar */}
      <div className="bcc-top-utility">
        <div className="bcc-utility-links">
          <span>Provincial Health Services Authority (PHSA)</span>
          <span style={{ color: "var(--border-subtle)" }}>|</span>
          <a href="#cerner">Cerner Millenium Bridge</a>
          <a href="#mirth">Mirth Connect ESB</a>
          <a href="#fhir">HAPI FHIR R4</a>
        </div>
        <div style={{ display: "flex", gap: "16px", alignItems: "center" }}>
          <span className="bcc-portal-badge">PEDIATRIC CLINICAL PORTAL</span>
          <span style={{ fontSize: "0.8rem", color: "var(--bcc-text-muted)" }}>
            Fraser Health / PHSA Node
          </span>
        </div>
      </div>

      {/* Main Header / Brand Area */}
      <div className="bcc-main-header">
        <div className="bcc-brand-group">
          {/* BC Cancer / Health Logo Tile */}
          <div className="bcc-logo-badge" aria-hidden="true">
            <span className="line-1">BC</span>
            <span className="line-2">CARE</span>
          </div>

          <div className="bcc-title-text">
            <h1>Langley Children's Hospital</h1>
            <p>Regional Pediatric Inpatient &amp; Immunization Synchronization Registry</p>
          </div>
        </div>

        <div className="bcc-header-status">
          <div
            className={`bcc-sync-chip ${isConnected ? "connected" : "disconnected"}`}
            role="status"
            aria-live="polite"
          >
            <span className="bcc-pulse-dot" aria-hidden="true"></span>
            <span>
              {isConnected
                ? isPolling
                  ? "Live Backend Connected (8081)"
                  : "Connected (Idle)"
                : "Backend Disconnected"}
            </span>
          </div>
        </div>
      </div>

      {/* Ice-Cyan BC Cancer Style Navigation Bar */}
      <nav className="bcc-nav-bar" aria-label="Main Navigation">
        <button className="bcc-nav-item active">Patient Registry</button>
        <button className="bcc-nav-item">Immunization Tracking</button>
        <button className="bcc-nav-item">Lab Profiles</button>
        <button className="bcc-nav-item">BC PHN Verification</button>
        <button className="bcc-nav-item">Interoperability (Port 8083/8085)</button>
        <button className="bcc-nav-item">Clinical Pathways</button>
      </nav>
    </header>
  );
}
