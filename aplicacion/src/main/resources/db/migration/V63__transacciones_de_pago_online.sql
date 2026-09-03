-- Intentos de cobro online: estado interno, idempotencia y trazabilidad segura.
-- No se guardan datos sensibles de tarjeta ni tokens del navegador.

CREATE TABLE payment_transactions (
    id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id                BIGINT UNSIGNED NOT NULL,
    order_id                 BIGINT UNSIGNED NULL,
    sale_id                  BIGINT UNSIGNED NULL,
    provider                 VARCHAR(20) NOT NULL,
    amount                   DECIMAL(12, 2) NOT NULL,
    currency_code            CHAR(3) NOT NULL DEFAULT 'PEN',
    status                   VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    idempotency_key          VARCHAR(100) NOT NULL,
    provider_transaction_id  VARCHAR(150) NULL,
    provider_reference       VARCHAR(255) NULL,
    failure_code             VARCHAR(100) NULL,
    failure_message          TEXT NULL,
    expires_at               DATETIME(6) NULL,
    completed_at             DATETIME(6) NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    created_at               DATETIME(6) NOT NULL,
    updated_at               DATETIME(6) NOT NULL,
    CONSTRAINT uk_payment_transaction_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_payment_transaction_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT fk_payment_transaction_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payment_transaction_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    -- El intento nace con order_id y, al aprobarse, tambiÃ©n conserva el sale_id
    -- generado. Ambas referencias son intencionales para trazabilidad.
    CONSTRAINT chk_payment_transaction_provider CHECK (provider IN ('NIUBIZ', 'CULQI', 'IZIPAY')),
    CONSTRAINT chk_payment_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_payment_transaction_status CHECK (status IN ('CREATED', 'PENDING', 'PROCESSING', 'APPROVED', 'DECLINED', 'FAILED', 'CANCELLED', 'REFUNDED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_transaction_tenant_status
    ON payment_transactions (tenant_id, status);
CREATE INDEX idx_payment_transaction_provider_transaction
    ON payment_transactions (tenant_id, provider, provider_transaction_id);
