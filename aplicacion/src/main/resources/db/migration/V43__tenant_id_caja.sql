-- Conversión a SaaS multi-tenant: cajas, sesiones de caja y movimientos de caja.

ALTER TABLE cash_registers
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_cash_registers_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE cash_sessions
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_cash_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE cash_movements
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_cash_movements_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
