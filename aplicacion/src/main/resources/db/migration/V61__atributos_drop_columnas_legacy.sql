-- Sistema de atributos genéricos por producto: limpieza final (Fase 5, ver plan aprobado).
-- Checkpoint explícito e irreversible sin restaurar un backup — corre recién después de probar
-- de punta a punta el tenant de ropa existente Y un tenant sin ningún atributo tipo SWATCH
-- (ferretería, ver VerticalNoRopaIntegrationTest), ambos en verde.
--
-- product_variants.color_id/size_id y las tablas colors/sizes ya no los lee ningún código: el
-- modelo de atributos genéricos (attributes/attribute_values/product_attributes/
-- variant_attribute_values) es la única fuente de verdad desde la Fase 2 de este mismo trabajo.

ALTER TABLE product_variants
    DROP FOREIGN KEY fk_variants_color,
    DROP FOREIGN KEY fk_variants_size,
    DROP INDEX uk_variants_combination,
    DROP COLUMN color_id,
    DROP COLUMN size_id;

DROP TABLE colors;
DROP TABLE sizes;
