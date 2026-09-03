-- Conversión a SaaS multi-tenant: promotores.

ALTER TABLE promoters
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_promoters_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
