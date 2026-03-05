-- Create diagnostic_report_flat table
CREATE TABLE IF NOT EXISTS diagnostic_report_flat (
    id VARCHAR(255) PRIMARY KEY,
    patient_id VARCHAR(255) NOT NULL,
    encounter_id VARCHAR(255),
    org_unit VARCHAR(255),  -- DHIS2 org unit UID (facility ID)
    status VARCHAR(50),
    conclusion TEXT,
    code_code VARCHAR(100),
    code_sys VARCHAR(255),
    code_display VARCHAR(255),
    result_obs_id VARCHAR(255),
    category_code VARCHAR(100),
    category_sys VARCHAR(255),
    conclusion_code VARCHAR(100),
    conclusion_sys VARCHAR(255),
    practitioner_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create observation_flat table
CREATE TABLE IF NOT EXISTS observation_flat (
    id VARCHAR(255) PRIMARY KEY,
    patient_id VARCHAR(255) NOT NULL,
    encounter_id VARCHAR(255),
    status VARCHAR(50),
    obs_date TIMESTAMP,
    val_quantity NUMERIC,
    val_quantity_unit VARCHAR(50),
    val_quantity_system VARCHAR(255),
    val_quantity_code VARCHAR(100),
    code_code VARCHAR(100),
    code_sys VARCHAR(255),
    code_display VARCHAR(255),
    value_code VARCHAR(100),
    value_sys VARCHAR(255),
    value_display VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create patient_flat table (referenced by the query)
CREATE TABLE IF NOT EXISTS patient_flat (
    id VARCHAR(255) PRIMARY KEY,
    gender VARCHAR(20),
    org_unit VARCHAR(255),  -- Which facility the patient is associated with
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_diagnostic_report_code ON diagnostic_report_flat(code_code);
CREATE INDEX idx_diagnostic_report_patient ON diagnostic_report_flat(patient_id);
CREATE INDEX idx_observation_obs_date ON observation_flat(obs_date);
CREATE INDEX idx_observation_patient ON observation_flat(patient_id);
CREATE INDEX idx_observation_code ON observation_flat(code_code);
