-- Ledger interno de comprobantes electrónicos. La emisión externa se mantiene
-- separada del pago y conserva un snapshot para reintentos sin recrear la venta.

ALTER TABLE billing_configurations
    ADD COLUMN invoice_series VARCHAR(10) NULL,
    ADD COLUMN receipt_series VARCHAR(10) NULL,
    ADD COLUMN credit_note_series VARCHAR(10) NULL,
    ADD COLUMN debit_note_series VARCHAR(10) NULL;

CREATE TABLE electronic_documents (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT UNSIGNED NOT NULL,
    sale_id               BIGINT UNSIGNED NOT NULL,
    source_document_id    BIGINT UNSIGNED NULL,
    provider              VARCHAR(20) NOT NULL DEFAULT 'VERIFACT',
    document_type         VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    series                VARCHAR(10) NOT NULL,
    document_number       VARCHAR(20) NOT NULL,
    idempotency_key       VARCHAR(100) NOT NULL,
    amount                DECIMAL(12, 2) NOT NULL,
    currency_code         CHAR(3) NOT NULL DEFAULT 'PEN',
    payload_json          LONGTEXT NOT NULL,
    provider_document_id  VARCHAR(150) NULL,
    provider_status       VARCHAR(40) NULL,
    cdr_code              VARCHAR(100) NULL,
    cdr_message           TEXT NULL,
    pdf_url               VARCHAR(1000) NULL,
    xml_url               VARCHAR(1000) NULL,
    cdr_url               VARCHAR(1000) NULL,
    submitted_at          DATETIME(6) NULL,
    accepted_at           DATETIME(6) NULL,
    rejected_at           DATETIME(6) NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_electronic_document_tenant_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT uk_electronic_document_tenant_sale_type UNIQUE (tenant_id, sale_id, document_type),
    CONSTRAINT uk_electronic_document_tenant_number UNIQUE (tenant_id, document_type, series, document_number),
    CONSTRAINT fk_electronic_document_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT fk_electronic_document_sale FOREIGN KEY (sale_id) REFERENCES sales (id),
    CONSTRAINT fk_electronic_document_source FOREIGN KEY (source_document_id) REFERENCES electronic_documents (id),
    CONSTRAINT chk_electronic_document_provider CHECK (provider IN ('VERIFACT')),
    CONSTRAINT chk_electronic_document_type CHECK (document_type IN ('BOLETA', 'FACTURA', 'NOTA_CREDITO', 'NOTA_DEBITO')),
    CONSTRAINT chk_electronic_document_status CHECK (status IN ('DRAFT', 'GENERATED', 'PENDING', 'SENT', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'ERROR')),
    CONSTRAINT chk_electronic_document_amount CHECK (amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_electronic_document_tenant_status
    ON electronic_documents (tenant_id, status);
