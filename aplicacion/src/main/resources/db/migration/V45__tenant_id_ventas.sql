-- Conversión a SaaS multi-tenant: ventas, sus líneas y sus pagos.

ALTER TABLE sales
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_sales_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE sale_details
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_sale_details_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE payments
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_payments_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
