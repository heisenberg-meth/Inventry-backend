-- ============================================
-- V9__create_system_configs.sql
-- Create system_configs table for global feature flags and support mode
-- ============================================

DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'system_configs') THEN
        CREATE TABLE system_configs (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
    END IF;
END $$;

-- Seed initial configs
INSERT INTO system_configs (config_key, config_value, description) VALUES 
('PHARMACY_EXTENSION_ENABLED', 'true', 'Global flag to enable/disable pharmacy extension'),
('SUPPORT_MODE', 'false', 'Global flag to allow ROOT users to bypass data isolation for support');
