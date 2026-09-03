-- Conversión a SaaS multi-tenant: sequences (los contadores correlativos de venta/pedido/
-- reserva/SKU) usa hoy el nombre de la secuencia ("VENTA", "PEDIDO", etc.) como PRIMARY KEY
-- directamente — no alcanza con agregar una columna, dos tenants no podrían tener ambos una
-- secuencia llamada "VENTA" con esa clave. Se reemplaza por un id autoincremental nuevo, y
-- (tenant_id, name) pasa a ser la combinación única. Efecto correcto además de necesario: cada
-- negocio nuevo empieza su propio conteo de ventas/pedidos desde cero, no comparte un contador
-- global con los demás.

ALTER TABLE sequences
    ADD COLUMN tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1,
    ADD COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT UNIQUE;

ALTER TABLE sequences
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id);

ALTER TABLE sequences
    ALTER COLUMN tenant_id DROP DEFAULT,
    ADD CONSTRAINT uk_sequences_tenant_name UNIQUE (tenant_id, name),
    ADD CONSTRAINT fk_sequences_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
