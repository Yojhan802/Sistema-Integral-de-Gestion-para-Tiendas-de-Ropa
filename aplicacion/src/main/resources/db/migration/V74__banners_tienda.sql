-- Banners administrables del storefront. La imagen se sube por endpoint protegido;
-- headline/CTA son texto controlado, no HTML ejecutable.
CREATE TABLE storefront_banners (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT UNSIGNED NOT NULL,
    image_url   VARCHAR(255) NULL,
    headline    VARCHAR(150) NULL,
    description TEXT NULL,
    cta_label   VARCHAR(80) NULL,
    cta_url     VARCHAR(255) NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_storefront_banners_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT chk_storefront_banners_sort_order CHECK (sort_order >= 0),
    CONSTRAINT chk_storefront_banners_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_storefront_banners_tenant_status_order
    ON storefront_banners (tenant_id, status, sort_order, id);
