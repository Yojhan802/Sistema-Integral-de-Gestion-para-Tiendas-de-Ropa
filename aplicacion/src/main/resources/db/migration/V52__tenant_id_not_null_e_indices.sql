-- Conversión a SaaS multi-tenant: ahora que V51 garantizó que ninguna fila tiene tenant_id
-- nulo, se vuelve NOT NULL en las 33 tablas — y se agrega un índice por tenant_id en cada una,
-- porque @TenantId de Hibernate agrega "WHERE tenant_id = ?" a prácticamente cualquier consulta
-- contra estas tablas (ver Fase 0 del plan): sin índice, eso sería un problema real de
-- rendimiento bajo la carga que se espera (~1500 usuarios concurrentes).

ALTER TABLE users MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_users_tenant (tenant_id);
ALTER TABLE roles MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_roles_tenant (tenant_id);
ALTER TABLE refresh_tokens MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_refresh_tokens_tenant (tenant_id);
ALTER TABLE audit_logs MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_audit_logs_tenant (tenant_id);

ALTER TABLE categories MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_categories_tenant (tenant_id);
ALTER TABLE subcategories MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_subcategories_tenant (tenant_id);
ALTER TABLE brands MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_brands_tenant (tenant_id);
ALTER TABLE colors MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_colors_tenant (tenant_id);
ALTER TABLE sizes MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_sizes_tenant (tenant_id);

ALTER TABLE products MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_products_tenant (tenant_id);
ALTER TABLE product_variants MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_variants_tenant (tenant_id);

ALTER TABLE branches MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_branches_tenant (tenant_id);
ALTER TABLE warehouses MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_warehouses_tenant (tenant_id);
ALTER TABLE inventory_movements MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_inventory_movements_tenant (tenant_id);

ALTER TABLE cash_registers MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_cash_registers_tenant (tenant_id);
ALTER TABLE cash_sessions MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_cash_sessions_tenant (tenant_id);
ALTER TABLE cash_movements MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_cash_movements_tenant (tenant_id);

ALTER TABLE payment_methods MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_payment_methods_tenant (tenant_id);

ALTER TABLE sales MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_sales_tenant (tenant_id);
ALTER TABLE sale_details MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_sale_details_tenant (tenant_id);
ALTER TABLE payments MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_payments_tenant (tenant_id);

ALTER TABLE customers MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_customers_tenant (tenant_id);
ALTER TABLE customer_refresh_tokens MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_customer_refresh_tokens_tenant (tenant_id);

ALTER TABLE returns MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_returns_tenant (tenant_id);
ALTER TABLE return_details MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_return_details_tenant (tenant_id);

ALTER TABLE combos MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_combos_tenant (tenant_id);
ALTER TABLE combo_items MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_combo_items_tenant (tenant_id);
ALTER TABLE promotions MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_promotions_tenant (tenant_id);

ALTER TABLE orders MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_orders_tenant (tenant_id);
ALTER TABLE order_details MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_order_details_tenant (tenant_id);
ALTER TABLE reservations MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_reservations_tenant (tenant_id);
ALTER TABLE reservation_details MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_reservation_details_tenant (tenant_id);

ALTER TABLE promoters MODIFY COLUMN tenant_id BIGINT UNSIGNED NOT NULL, ADD INDEX idx_promoters_tenant (tenant_id);
