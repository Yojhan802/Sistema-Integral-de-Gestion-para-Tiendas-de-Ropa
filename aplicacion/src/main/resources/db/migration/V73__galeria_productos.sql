-- Galeria publica de productos. La portada existente se conserva en products.image_url
-- por compatibilidad y se replica como imagen principal.
CREATE TABLE product_images (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT UNSIGNED NOT NULL,
    product_id  BIGINT UNSIGNED NOT NULL,
    image_url   VARCHAR(255) NOT NULL,
    alt_text    VARCHAR(150) NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT fk_product_images_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT chk_product_images_sort_order CHECK (sort_order >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_product_images_tenant_product
    ON product_images (tenant_id, product_id, sort_order, id);

INSERT INTO product_images (tenant_id, product_id, image_url, sort_order, is_primary, created_at, updated_at)
SELECT tenant_id, id, image_url, 0, TRUE, created_at, updated_at
FROM products
WHERE image_url IS NOT NULL AND image_url <> '';
