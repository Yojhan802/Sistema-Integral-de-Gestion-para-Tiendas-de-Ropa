-- Apariencia publicada por tenant. Solo almacena valores controlados, nunca HTML/CSS arbitrario.
ALTER TABLE company_settings
    ADD COLUMN store_primary_color VARCHAR(7) NOT NULL DEFAULT '#17324D' AFTER store_template,
    ADD COLUMN store_accent_color VARCHAR(7) NOT NULL DEFAULT '#17324D' AFTER store_primary_color,
    ADD COLUMN store_background_color VARCHAR(7) NOT NULL DEFAULT '#F5F7FA' AFTER store_accent_color,
    ADD CONSTRAINT chk_company_settings_store_primary_color CHECK (store_primary_color REGEXP '^#[0-9A-Fa-f]{6}$'),
    ADD CONSTRAINT chk_company_settings_store_accent_color CHECK (store_accent_color REGEXP '^#[0-9A-Fa-f]{6}$'),
    ADD CONSTRAINT chk_company_settings_store_background_color CHECK (store_background_color REGEXP '^#[0-9A-Fa-f]{6}$');
