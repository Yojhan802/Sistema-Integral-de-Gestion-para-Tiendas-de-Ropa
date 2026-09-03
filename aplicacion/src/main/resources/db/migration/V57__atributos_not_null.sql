-- Sistema de atributos genéricos por producto: ahora que V56 garantizó que ninguna fila
-- quedó sin variant_label/combination_hash, se vuelven NOT NULL y se agrega la restricción
-- de unicidad real. product_variants ya tiene idx_variants_product(product_id) standalone
-- desde V53 (agregado ahí porque MySQL exige un índice de soporte para fk_variants_product
-- en todo momento), así que esta ALTER no debería repetir el error 1553 visto en V53 — se
-- verifica de todas formas contra MySQL real antes de dar la fase por cerrada.

ALTER TABLE product_variants
    MODIFY COLUMN variant_label VARCHAR(150) NOT NULL,
    MODIFY COLUMN combination_hash CHAR(64) NOT NULL,
    ADD CONSTRAINT uk_variants_combination_hash UNIQUE (tenant_id, product_id, combination_hash);
