-- V3: Create Users Table for SMART-on-FHIR OAuth2 Identity Authentication
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(50) NOT NULL,
    default_patient_id VARCHAR(50),
    badge_rfid VARCHAR(100) UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Initial BC Health Authority Clinician and Patient Users (synthetic demo accounts)
-- Passwords should be managed via environment secrets or identity provider in production
INSERT INTO users (username, email, password_hash, full_name, role, default_patient_id, badge_rfid)
VALUES 
(
    'dr.patel', 
    'dr.patel@phsa.ca', 
    '$2a$10$e8wE4z9Hj7q2R7T9uL5O2eX3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M', 
    'Dr. Priya Patel (BC Cancer Specialist)', 
    'ROLE_ONCOLOGIST', 
    '1',
    'BADGE-PHSA-88901'
),
(
    'sarah.jenkins', 
    'sarah.jenkins@patient.ca', 
    '$2a$10$e8wE4z9Hj7q2R7T9uL5O2eX3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M', 
    'Sarah Jenkins (Patient)', 
    'ROLE_PATIENT', 
    '1',
    'BADGE-PAT-10001'
)
ON CONFLICT (username) DO NOTHING;
