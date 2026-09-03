-- Conversión a SaaS multi-tenant: devoluciones y sus líneas.

ALTER TABLE returns
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_returns_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE return_details
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_return_details_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
