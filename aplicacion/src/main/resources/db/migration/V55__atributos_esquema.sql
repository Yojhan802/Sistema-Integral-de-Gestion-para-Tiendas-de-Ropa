-- Sistema de atributos genéricos por producto (reemplaza color/talla, ver plan aprobado):
-- este primer paso solo crea las tablas nuevas y agrega columnas nullable a
-- product_variants. Aditivo puro — color_id/size_id siguen NOT NULL y siguen siendo lo
-- que usa la app; el backfill (V56) llena los datos, V57 recién exige NOT NULL.

CREATE TABLE attributes (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id  BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(40)   NOT NULL,
    input_type VARCHAR(20)   NOT NULL,
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attributes_name UNIQUE (tenant_id, name),
    KEY idx_attributes_tenant (tenant_id),
    CONSTRAINT fk_attributes_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT chk_attributes_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_attributes_input_type CHECK (input_type IN ('SWATCH', 'LIST'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE attribute_values (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT UNSIGNED NOT NULL,
    attribute_id BIGINT UNSIGNED NOT NULL,
    value        VARCHAR(40)   NOT NULL,
    hex_code     CHAR(7)       NULL,
    sort_order   SMALLINT      NOT NULL DEFAULT 0,
    status       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attribute_values_value UNIQUE (tenant_id, attribute_id, value),
    KEY idx_attribute_values_tenant (tenant_id),
    CONSTRAINT fk_attribute_values_attribute FOREIGN KEY (attribute_id) REFERENCES attributes (id) ON DELETE CASCADE,
    CONSTRAINT fk_attribute_values_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id),
    CONSTRAINT chk_attribute_values_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Qué atributos usa un producto y en qué orden (reemplaza "color siempre primero, talla
-- siempre segundo" — ver Product.java/producto-detalle.js tras el rediseño).
CREATE TABLE product_attributes (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id    BIGINT UNSIGNED NOT NULL,
    product_id   BIGINT UNSIGNED NOT NULL,
    attribute_id BIGINT UNSIGNED NOT NULL,
    position     SMALLINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_attributes_attribute UNIQUE (tenant_id, product_id, attribute_id),
    CONSTRAINT uk_product_attributes_position UNIQUE (tenant_id, product_id, position),
    KEY idx_product_attributes_tenant (tenant_id),
    CONSTRAINT fk_product_attributes_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_attributes_attribute FOREIGN KEY (attribute_id) REFERENCES attributes (id),
    CONSTRAINT fk_product_attributes_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Reemplaza las 2 FKs fijas (color_id/size_id) de product_variants por N filas.
CREATE TABLE variant_attribute_values (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT UNSIGNED NOT NULL,
    variant_id          BIGINT UNSIGNED NOT NULL,
    attribute_value_id  BIGINT UNSIGNED NOT NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_variant_attribute_values UNIQUE (tenant_id, variant_id, attribute_value_id),
    KEY idx_variant_attribute_values_tenant (tenant_id),
    KEY idx_variant_attribute_values_value (attribute_value_id),
    CONSTRAINT fk_variant_attribute_values_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE,
    CONSTRAINT fk_variant_attribute_values_value FOREIGN KEY (attribute_value_id) REFERENCES attribute_values (id),
    CONSTRAINT fk_variant_attribute_values_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Nullable por ahora: V56 los llena desde color_id/size_id, V57 recién exige NOT NULL.
-- sku se ensancha de 60 a 80 aprovechando que ya se toca la tabla — con más de 2
-- atributos el SKU autogenerado puede necesitar más espacio que "{SKU}-{TALLA}-{COLOR}".
ALTER TABLE product_variants
    ADD COLUMN variant_label     VARCHAR(150) NULL AFTER size_id,
    ADD COLUMN combination_hash  CHAR(64)     NULL AFTER variant_label,
    MODIFY COLUMN sku VARCHAR(80) NOT NULL;
