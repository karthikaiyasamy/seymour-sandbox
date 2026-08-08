-- ==============================================================================
-- SeymorFHIR Database Migration: V2__create_oauth_keys_table.sql
-- Persistent RSA 2048-bit Key Store DDL for SMART-on-FHIR RS256 JWT Signing
-- ==============================================================================

CREATE TABLE IF NOT EXISTS oauth_keys (
    key_id VARCHAR(255) PRIMARY KEY,
    private_key_pem TEXT NOT NULL,
    public_key_pem TEXT NOT NULL,
    algorithm VARCHAR(50) DEFAULT 'RS256',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookup of active signing keys
CREATE INDEX IF NOT EXISTS idx_oauth_keys_active ON oauth_keys(is_active);
