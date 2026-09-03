-- Conversión a SaaS multi-tenant: pedidos online (orders) y separaciones (reservations).

ALTER TABLE orders
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_orders_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE order_details
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_order_details_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE reservations
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_reservations_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE reservation_details
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_reservation_details_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
