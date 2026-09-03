CREATE TABLE complaint_book_entries (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id                   BIGINT UNSIGNED NOT NULL,
    entry_number                VARCHAR(30) NOT NULL,
    entry_type                  VARCHAR(10) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    provider_name               VARCHAR(150) NOT NULL,
    provider_ruc                VARCHAR(11) NULL,
    provider_address            VARCHAR(255) NULL,
    consumer_name               VARCHAR(150) NOT NULL,
    consumer_document           VARCHAR(20) NULL,
    consumer_email              VARCHAR(150) NOT NULL,
    consumer_phone              VARCHAR(20) NULL,
    order_number                VARCHAR(30) NULL,
    sale_number                 VARCHAR(30) NULL,
    product_service_description VARCHAR(255) NOT NULL,
    amount                      DECIMAL(12,2) NULL,
    detail                      TEXT NOT NULL,
    consumer_request            TEXT NOT NULL,
    response                    TEXT NULL,
    responded_at                DATETIME(6) NULL,
    responded_by                BIGINT UNSIGNED NULL,
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    CONSTRAINT uk_complaint_book_entry UNIQUE (tenant_id, entry_number),
    CONSTRAINT fk_complaint_book_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT fk_complaint_book_responder FOREIGN KEY (responded_by) REFERENCES users (id),
    CONSTRAINT chk_complaint_book_type CHECK (entry_type IN ('RECLAMO', 'QUEJA')),
    CONSTRAINT chk_complaint_book_status CHECK (status IN ('PENDIENTE', 'RESPONDIDO', 'CERRADO')),
    CONSTRAINT chk_complaint_book_amount CHECK (amount IS NULL OR amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_complaint_book_created ON complaint_book_entries (tenant_id, created_at);
CREATE INDEX idx_complaint_book_status ON complaint_book_entries (tenant_id, status);

INSERT INTO permissions (code, module, description)
VALUES
    ('RECLAMOS_CONSULTAR', 'RECLAMOS', 'Consultar hojas del Libro de Reclamaciones'),
    ('RECLAMOS_RESPONDER', 'RECLAMOS', 'Responder hojas del Libro de Reclamaciones')
ON DUPLICATE KEY UPDATE description = VALUES(description), module = VALUES(module);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ADMINISTRADOR', 'SUPERVISOR')
  AND p.code IN ('RECLAMOS_CONSULTAR', 'RECLAMOS_RESPONDER');
