-- Conversión a SaaS multi-tenant: clientes de la tienda online y sus refresh tokens.

ALTER TABLE customers
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE customer_refresh_tokens
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_customer_refresh_tokens_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
