-- Historial de cambios de paquete por empresa.
--
-- La tabla `tenant_modules` solo guarda el estado actual, así que cuando un cliente
-- discute una factura no hay forma de saber quién le subió o le bajó el paquete ni
-- cuándo. Aquí queda cada cambio con su importe antes y después.
--
-- El usuario se guarda además por nombre: si más adelante se da de baja, el historial
-- tiene que seguir diciendo quién hizo el cambio.

CREATE TABLE tenant_module_changes (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT UNSIGNED NOT NULL,
    changed_at          DATETIME(6) NOT NULL,
    changed_by          BIGINT UNSIGNED NULL,
    changed_by_username VARCHAR(50) NULL,
    previous_total      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    new_total           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    -- Códigos separados por coma; se leen para mostrar, nunca para decidir acceso.
    added               VARCHAR(500) NULL,
    removed             VARCHAR(500) NULL,
    modules             VARCHAR(500) NULL,
    CONSTRAINT fk_tenant_module_changes_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT fk_tenant_module_changes_user FOREIGN KEY (changed_by) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_tenant_module_changes ON tenant_module_changes (tenant_id, changed_at DESC);
