-- ==============================================================================
-- SeymorFHIR Database Migration: V1__create_oauth_tables.sql
-- SMART on FHIR OAuth2 Authorization & Access Token DDL + Synthetic Seed Data
-- ==============================================================================

-- 1. OAuth2 Authorization Codes Table
CREATE TABLE IF NOT EXISTS oauth_authorization_codes (
    code VARCHAR(255) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    patient_id VARCHAR(255) NOT NULL,
    redirect_uri VARCHAR(255),
    scope VARCHAR(255),
    state VARCHAR(255),
    created_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_consumed BOOLEAN DEFAULT FALSE
);

-- 2. OAuth2 Access Tokens Table
CREATE TABLE IF NOT EXISTS oauth_access_tokens (
    token VARCHAR(255) PRIMARY KEY,
    client_id VARCHAR(255) NOT NULL,
    patient_id VARCHAR(255) NOT NULL,
    scope VARCHAR(255) NOT NULL,
    token_type VARCHAR(50) DEFAULT 'Bearer',
    created_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

-- 3. Synthetic Seed Data for SMART-on-FHIR Integration Testing
INSERT INTO oauth_authorization_codes (code, client_id, patient_id, redirect_uri, scope, created_at, expires_at, is_consumed)
VALUES ('SMART_AUTH_SYNC', 'seymour_smart_app', '1', 'http://localhost:3000/callback', 'launch/patient patient/*.read', NOW(), NOW() + INTERVAL '1 hour', FALSE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO oauth_access_tokens (token, client_id, patient_id, scope, token_type, created_at, expires_at)
VALUES ('SMART_BEARER_TEST_TOKEN', 'seymour_smart_app', '1', 'launch/patient patient/*.read', 'Bearer', NOW(), NOW() + INTERVAL '24 hours')
ON CONFLICT (token) DO NOTHING;
