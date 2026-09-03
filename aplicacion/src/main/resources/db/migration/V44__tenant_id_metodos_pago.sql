-- Conversión a SaaS multi-tenant: métodos de pago (cada negocio configura los propios).

ALTER TABLE payment_methods
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_payment_methods_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
