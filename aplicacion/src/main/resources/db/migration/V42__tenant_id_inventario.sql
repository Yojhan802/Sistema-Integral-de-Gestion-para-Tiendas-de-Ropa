-- Conversión a SaaS multi-tenant: sucursales, almacenes y movimientos de inventario.

ALTER TABLE branches
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_branches_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE warehouses
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_warehouses_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE inventory_movements
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_inventory_movements_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
