-- Pagos de suscripción de cada empresa.
--
-- Hasta ahora renovar era abrir «Editar» y correr `next_payment_due` a mano: no quedaba
-- cuánto pagó, cómo ni qué periodo cubría, y esa fecha se pisa en cada renovación. Aquí
-- queda cada cobro, que es el sustento cuando un cliente discute una mensualidad.
--
-- `source` distingue el registro manual del cobro online: hoy solo se usa MANUAL, pero
-- deja el hueco para que el cliente pague por pasarela sin cambiar el modelo.

CREATE TABLE subscription_payments (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id              BIGINT UNSIGNED NOT NULL,
    paid_at                DATETIME(6) NOT NULL,
    amount                 DECIMAL(10,2) NOT NULL,
    method                 VARCHAR(30) NOT NULL,
    reference              VARCHAR(80) NULL,
    -- Periodo cubierto por este pago; encadena con el vencimiento anterior.
    period_start           DATE NOT NULL,
    period_end             DATE NOT NULL,
    source                 VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    registered_by          BIGINT UNSIGNED NULL,
    registered_by_username VARCHAR(50) NULL,
    notes                  VARCHAR(255) NULL,
    CONSTRAINT fk_subscription_payments_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT fk_subscription_payments_user FOREIGN KEY (registered_by) REFERENCES users (id),
    CONSTRAINT chk_subscription_payments_amount CHECK (amount >= 0),
    CONSTRAINT chk_subscription_payments_period CHECK (period_end > period_start),
    CONSTRAINT chk_subscription_payments_source CHECK (source IN ('MANUAL', 'ONLINE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_subscription_payments_tenant ON subscription_payments (tenant_id, paid_at DESC);
