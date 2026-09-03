-- Conversión a SaaS multi-tenant: todo lo que ya existía en el sistema pertenece al primer
-- negocio (tenant_id = 1, la fila company_settings que ya existía antes de esta conversión,
-- ver V38). Corre después de que las 33 tablas de negocio ya tienen la columna tenant_id
-- (V39-V50), antes de que otra migración la vuelva NOT NULL (V52).

UPDATE users SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE roles SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE refresh_tokens SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE audit_logs SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE categories SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE subcategories SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE brands SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE colors SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sizes SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE products SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE product_variants SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE branches SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE warehouses SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE inventory_movements SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE cash_registers SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE cash_sessions SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE cash_movements SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE payment_methods SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE sales SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sale_details SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE payments SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE customers SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE customer_refresh_tokens SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE returns SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE return_details SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE combos SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE combo_items SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE promotions SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE orders SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE order_details SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE reservations SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE reservation_details SET tenant_id = 1 WHERE tenant_id IS NULL;

UPDATE promoters SET tenant_id = 1 WHERE tenant_id IS NULL;
