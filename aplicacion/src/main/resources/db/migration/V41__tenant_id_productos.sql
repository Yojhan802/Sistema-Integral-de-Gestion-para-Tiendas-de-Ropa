-- Conversión a SaaS multi-tenant: productos y variantes.

ALTER TABLE products
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_products_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE product_variants
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_variants_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
