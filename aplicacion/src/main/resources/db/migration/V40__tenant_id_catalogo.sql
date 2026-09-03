-- Conversión a SaaS multi-tenant: catálogo (categorías, subcategorías, marcas, colores, tallas).

ALTER TABLE categories
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_categories_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE subcategories
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_subcategories_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE brands
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_brands_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE colors
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_colors_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE sizes
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_sizes_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
