-- Índices para las consultas paginadas y los procesos periódicos del SaaS.
--
-- Hibernate añade tenant_id mediante @TenantId. Los índices simples que se
-- crearon en V52 permiten filtrar por empresa, pero no evitan recorrer y
-- ordenar grandes subconjuntos cuando además se filtra por estado/fecha.
-- No se eliminan índices anteriores: algunos sostienen claves foráneas o
-- consultas distintas.

CREATE INDEX idx_sales_tenant_status_created
    ON sales (tenant_id, status, created_at, id);

CREATE INDEX idx_orders_tenant_status_created
    ON orders (tenant_id, status, created_at, id);

CREATE INDEX idx_orders_tenant_customer_created
    ON orders (tenant_id, customer_id, created_at, id);

CREATE INDEX idx_inventory_movements_tenant_created
    ON inventory_movements (tenant_id, created_at, id);

CREATE INDEX idx_inventory_movements_tenant_variant_created
    ON inventory_movements (tenant_id, variant_id, created_at, id);

CREATE INDEX idx_product_variants_tenant_status_stock_updated
    ON product_variants (tenant_id, status, stock, updated_at, id);

CREATE INDEX idx_payment_transactions_tenant_created
    ON payment_transactions (tenant_id, created_at, provider, status);

CREATE INDEX idx_electronic_documents_tenant_status_submitted
    ON electronic_documents (tenant_id, status, submitted_at, id);

CREATE INDEX idx_electronic_documents_tenant_created
    ON electronic_documents (tenant_id, created_at, provider, status);

CREATE INDEX idx_cash_sessions_tenant_status_opened
    ON cash_sessions (tenant_id, status, opened_at, id);

CREATE INDEX idx_cash_sessions_tenant_status_closed
    ON cash_sessions (tenant_id, status, closed_at, id);

CREATE INDEX idx_audit_logs_tenant_created
    ON audit_logs (tenant_id, created_at, id);
