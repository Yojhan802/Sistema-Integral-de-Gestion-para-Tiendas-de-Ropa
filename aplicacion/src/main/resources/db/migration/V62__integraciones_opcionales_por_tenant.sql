-- Integraciones externas opcionales e independientes por tenant.
-- Los valores FALSE preservan el comportamiento actual: pagos manuales y nota interna.

ALTER TABLE company_settings
    ADD COLUMN online_payments_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER next_payment_due,
    ADD COLUMN electronic_invoicing_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER online_payments_enabled;

CREATE TABLE payment_provider_configurations (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT UNSIGNED NOT NULL,
    provider              VARCHAR(20) NOT NULL,
    enabled               BOOLEAN NOT NULL DEFAULT FALSE,
    environment           VARCHAR(20) NOT NULL DEFAULT 'TEST',
    api_url               VARCHAR(500) NULL,
    merchant_code         VARCHAR(100) NULL,
    public_key            VARCHAR(500) NULL,
    credentials_encrypted TEXT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_payment_provider_config_tenant_provider UNIQUE (tenant_id, provider),
    CONSTRAINT fk_payment_provider_config_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT chk_payment_provider_config_provider CHECK (provider IN ('NIUBIZ', 'CULQI', 'IZIPAY')),
    CONSTRAINT chk_payment_provider_config_environment CHECK (environment IN ('TEST', 'PRODUCTION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_payment_provider_config_tenant ON payment_provider_configurations (tenant_id);

CREATE TABLE billing_configurations (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id             BIGINT UNSIGNED NOT NULL,
    provider              VARCHAR(20) NOT NULL DEFAULT 'VERIFACT',
    enabled               BOOLEAN NOT NULL DEFAULT FALSE,
    environment           VARCHAR(20) NOT NULL DEFAULT 'TEST',
    api_url               VARCHAR(500) NULL,
    credentials_encrypted TEXT NULL,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    CONSTRAINT uk_billing_config_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_billing_config_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT chk_billing_config_provider CHECK (provider IN ('VERIFACT')),
    CONSTRAINT chk_billing_config_environment CHECK (environment IN ('TEST', 'PRODUCTION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_billing_config_tenant ON billing_configurations (tenant_id);
