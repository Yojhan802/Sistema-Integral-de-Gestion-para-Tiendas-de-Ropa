-- Conversión a SaaS multi-tenant: combos, sus líneas y promociones.

ALTER TABLE combos
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_combos_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE combo_items
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_combo_items_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE promotions
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_promotions_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
