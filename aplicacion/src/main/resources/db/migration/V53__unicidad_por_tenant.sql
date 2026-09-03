-- Conversión a SaaS multi-tenant: las restricciones UNIQUE que hoy son globales pasan a ser
-- compuestas (tenant_id, columna) — dos negocios distintos ya podrán tener ambos un usuario
-- "admin" o un SKU "POL-001" sin chocar entre sí.
--
-- Quedan SIN cambiar a propósito:
--   - uk_permissions_code: permissions es el catálogo fijo de la aplicación, global (no lleva
--     tenant_id, ver BaseEntity.tenantId).
--   - uk_refresh_tokens_hash / uk_customer_refresh_tokens_hash: un hash SHA-256 ya es único
--     globalmente por construcción — acotarlo por tenant no aporta nada.
--   - uk_one_open_session (cash_sessions.open_register_id): ya está acotada transitivamente,
--     porque cash_register_id pertenece a una sola cash_registers.id, que ya es de un tenant.

ALTER TABLE branches DROP INDEX uk_branches_code, ADD CONSTRAINT uk_branches_code UNIQUE (tenant_id, code);
ALTER TABLE brands DROP INDEX uk_brands_name, ADD CONSTRAINT uk_brands_name UNIQUE (tenant_id, name);
ALTER TABLE cash_registers DROP INDEX uk_cash_registers_code, ADD CONSTRAINT uk_cash_registers_code UNIQUE (tenant_id, code);
ALTER TABLE categories DROP INDEX uk_categories_name, ADD CONSTRAINT uk_categories_name UNIQUE (tenant_id, name);
ALTER TABLE categories DROP INDEX uk_categories_slug, ADD CONSTRAINT uk_categories_slug UNIQUE (tenant_id, slug);
ALTER TABLE colors DROP INDEX uk_colors_name, ADD CONSTRAINT uk_colors_name UNIQUE (tenant_id, name);
ALTER TABLE combos DROP INDEX uk_combos_code, ADD CONSTRAINT uk_combos_code UNIQUE (tenant_id, code);
ALTER TABLE customers DROP INDEX uk_customers_doc_number, ADD CONSTRAINT uk_customers_doc_number UNIQUE (tenant_id, doc_number);
ALTER TABLE customers DROP INDEX uk_customers_email, ADD CONSTRAINT uk_customers_email UNIQUE (tenant_id, email);
ALTER TABLE orders DROP INDEX uk_orders_number, ADD CONSTRAINT uk_orders_number UNIQUE (tenant_id, order_number);
ALTER TABLE payment_methods DROP INDEX uk_payment_methods_code, ADD CONSTRAINT uk_payment_methods_code UNIQUE (tenant_id, code);
ALTER TABLE product_variants DROP INDEX uk_variants_barcode, ADD CONSTRAINT uk_variants_barcode UNIQUE (tenant_id, barcode);
ALTER TABLE product_variants DROP INDEX uk_variants_sku, ADD CONSTRAINT uk_variants_sku UNIQUE (tenant_id, sku);
-- uk_variants_combination era el único índice que empezaba por product_id, y MySQL lo exige para
-- sostener fk_variants_product — al anteponer tenant_id dejaría de servir. Se agrega un índice
-- propio para la FK antes de reemplazar el UNIQUE (visto al aplicar esto contra MySQL real: Error
-- 1553 "Cannot drop index ... needed in a foreign key constraint").
ALTER TABLE product_variants ADD INDEX idx_variants_product (product_id);
ALTER TABLE product_variants DROP INDEX uk_variants_combination, ADD CONSTRAINT uk_variants_combination UNIQUE (tenant_id, product_id, color_id, size_id);
ALTER TABLE products DROP INDEX uk_products_internal_code, ADD CONSTRAINT uk_products_internal_code UNIQUE (tenant_id, internal_code);
ALTER TABLE products DROP INDEX uk_products_sku, ADD CONSTRAINT uk_products_sku UNIQUE (tenant_id, sku);
ALTER TABLE promotions DROP INDEX uk_promotions_code, ADD CONSTRAINT uk_promotions_code UNIQUE (tenant_id, code);
ALTER TABLE reservations DROP INDEX uk_reservations_number, ADD CONSTRAINT uk_reservations_number UNIQUE (tenant_id, reservation_number);
ALTER TABLE returns DROP INDEX uk_returns_number, ADD CONSTRAINT uk_returns_number UNIQUE (tenant_id, return_number);
ALTER TABLE roles DROP INDEX uk_roles_code, ADD CONSTRAINT uk_roles_code UNIQUE (tenant_id, code);
ALTER TABLE sales DROP INDEX uk_sales_number, ADD CONSTRAINT uk_sales_number UNIQUE (tenant_id, sale_number);
ALTER TABLE sizes DROP INDEX uk_sizes_name, ADD CONSTRAINT uk_sizes_name UNIQUE (tenant_id, name);
-- Mismo caso que product_variants arriba: uk_subcategories_category_name sostenía fk_subcategories_category.
ALTER TABLE subcategories ADD INDEX idx_subcategories_category (category_id);
ALTER TABLE subcategories DROP INDEX uk_subcategories_category_name, ADD CONSTRAINT uk_subcategories_category_name UNIQUE (tenant_id, category_id, name);
ALTER TABLE users DROP INDEX uk_users_dni, ADD CONSTRAINT uk_users_dni UNIQUE (tenant_id, dni);
ALTER TABLE users DROP INDEX uk_users_email, ADD CONSTRAINT uk_users_email UNIQUE (tenant_id, email);
ALTER TABLE users DROP INDEX uk_users_username, ADD CONSTRAINT uk_users_username UNIQUE (tenant_id, username);
ALTER TABLE warehouses DROP INDEX uk_warehouses_code, ADD CONSTRAINT uk_warehouses_code UNIQUE (tenant_id, code);
