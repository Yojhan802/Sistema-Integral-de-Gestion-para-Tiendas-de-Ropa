-- Módulos contratados por empresa.
--
-- Hasta ahora el acceso lo decidía `company_settings.plan`, una escalera acumulativa
-- (STARTER < PROFESIONAL < ECOMMERCE < IA) comparada por ordinal. Eso impedía vender
-- combinaciones reales: quien solo quiere la tienda online tenía que pagar el plan
-- Ecommerce completo, con separaciones y combos que no usa.
--
-- A partir de aquí el acceso lo decide este conjunto por empresa. El plan se conserva
-- como preset comercial: elegirlo siembra unos módulos, que luego se ajustan a mano
-- para encajar en el presupuesto del cliente.
--
-- Las dependencias entre módulos NO se guardan aquí: viven en ModuloSistema porque son
-- un hecho del código, no una decisión del operador.

CREATE TABLE tenant_modules (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT UNSIGNED NOT NULL,
    module_code   VARCHAR(30) NOT NULL,
    -- Precio pactado con esta empresa; arranca en el de lista y se puede negociar.
    monthly_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    CONSTRAINT uk_tenant_module UNIQUE (tenant_id, module_code),
    CONSTRAINT fk_tenant_module_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT chk_tenant_module_price CHECK (monthly_price >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_tenant_modules_tenant ON tenant_modules (tenant_id);

-- Backfill: cada empresa conserva exactamente lo que su plan ya le daba, para que
-- nadie pierda acceso al desplegar. Refleja ModuloSistema.delPlan(); si esa tabla
-- cambia en el futuro, esto no se toca — es la foto del momento de la migración.
INSERT INTO tenant_modules (tenant_id, module_code, monthly_price, created_at, updated_at)
SELECT c.id, m.code, m.price, NOW(6), NOW(6)
FROM company_settings c
JOIN (
    SELECT 'PRODUCTOS' AS code, 15.00 AS price, 0 AS desde UNION ALL
    SELECT 'INVENTARIO',         12.00,         0 UNION ALL
    SELECT 'CLIENTES',            8.00,         0 UNION ALL
    SELECT 'CAJA',               10.00,         0 UNION ALL
    SELECT 'POS',                20.00,         0 UNION ALL
    SELECT 'REPORTES',           10.00,         0 UNION ALL
    SELECT 'SEPARACIONES',        8.00,         1 UNION ALL
    SELECT 'COMBOS',              6.00,         1 UNION ALL
    SELECT 'PROMOCIONES',         6.00,         1 UNION ALL
    SELECT 'PROMOTORES',          6.00,         1 UNION ALL
    SELECT 'AUDITORIA',           8.00,         1 UNION ALL
    SELECT 'TIENDA',             25.00,         2 UNION ALL
    SELECT 'FACTURACION',        18.00,         2 UNION ALL
    SELECT 'RECLAMOS',            0.00,         2 UNION ALL
    SELECT 'IA',                 25.00,         3
) m ON m.desde <= CASE c.plan
        WHEN 'STARTER'     THEN 0
        WHEN 'PROFESIONAL' THEN 1
        WHEN 'ECOMMERCE'   THEN 2
        WHEN 'IA'          THEN 3
        ELSE 0
    END;
