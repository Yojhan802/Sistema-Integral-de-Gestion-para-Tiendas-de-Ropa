-- Sistema de atributos genéricos por producto: backfill puro de datos. Las columnas
-- viejas (colors/sizes/product_variants.color_id/size_id) son la fuente de verdad —
-- nada se borra ni se toca ahí, esto solo puebla las tablas nuevas de V55.
--
-- El hash de combinación se calcula igual que hará VarianteService (Fase 2): SHA-256
-- sobre la lista de attribute_value_id ordenada ascendente, unida por coma — verificado
-- en el spike de la Fase 0 contra MySQL real. Con exactamente 2 valores por variante acá,
-- LEAST/GREATEST reproduce ese mismo orden ascendente.

-- 1) Un atributo "Color" (SWATCH) y uno "Talla" (LIST) por tenant que ya tenga datos.
INSERT INTO attributes (tenant_id, name, input_type, status, created_at, updated_at)
SELECT DISTINCT tenant_id, 'Color', 'SWATCH', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM colors;

INSERT INTO attributes (tenant_id, name, input_type, status, created_at, updated_at)
SELECT DISTINCT tenant_id, 'Talla', 'LIST', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM sizes;

-- 2) Un attribute_value por cada color/talla existente, preservando hex_code y
--    sort_order exactos (crítico para mantener el orden XS<S<M<L de tallas).
INSERT INTO attribute_values (tenant_id, attribute_id, value, hex_code, sort_order, status, created_at, updated_at)
SELECT c.tenant_id, a.id, c.name, c.hex_code, 0, c.status, c.created_at, c.updated_at
FROM colors c
JOIN attributes a ON a.tenant_id = c.tenant_id AND a.name = 'Color';

INSERT INTO attribute_values (tenant_id, attribute_id, value, hex_code, sort_order, status, created_at, updated_at)
SELECT s.tenant_id, a.id, s.name, NULL, s.sort_order, s.status, s.created_at, s.updated_at
FROM sizes s
JOIN attributes a ON a.tenant_id = s.tenant_id AND a.name = 'Talla';

-- 3) Qué atributos usa cada producto: Color en posición 1, Talla en posición 2 — igual
--    al orden fijo que tenía la UI hasta ahora.
INSERT INTO product_attributes (tenant_id, product_id, attribute_id, position, created_at, updated_at)
SELECT DISTINCT pv.tenant_id, pv.product_id, a.id, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM product_variants pv
JOIN attributes a ON a.tenant_id = pv.tenant_id AND a.name = 'Color';

INSERT INTO product_attributes (tenant_id, product_id, attribute_id, position, created_at, updated_at)
SELECT DISTINCT pv.tenant_id, pv.product_id, a.id, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM product_variants pv
JOIN attributes a ON a.tenant_id = pv.tenant_id AND a.name = 'Talla';

-- 4) Cada variante existente enlaza a su valor de Color y su valor de Talla.
INSERT INTO variant_attribute_values (tenant_id, variant_id, attribute_value_id, created_at, updated_at)
SELECT pv.tenant_id, pv.id, av.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM product_variants pv
JOIN colors c ON c.id = pv.color_id
JOIN attributes a ON a.tenant_id = pv.tenant_id AND a.name = 'Color'
JOIN attribute_values av ON av.attribute_id = a.id AND av.tenant_id = pv.tenant_id AND av.value = c.name;

INSERT INTO variant_attribute_values (tenant_id, variant_id, attribute_value_id, created_at, updated_at)
SELECT pv.tenant_id, pv.id, av.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM product_variants pv
JOIN sizes s ON s.id = pv.size_id
JOIN attributes a ON a.tenant_id = pv.tenant_id AND a.name = 'Talla'
JOIN attribute_values av ON av.attribute_id = a.id AND av.tenant_id = pv.tenant_id AND av.value = s.name;

-- 5) variant_label / combination_hash sobre product_variants, calculados directo desde
--    color_id/size_id (más simple que ir y volver por las tablas nuevas para este paso).
UPDATE product_variants pv
JOIN colors c ON c.id = pv.color_id
JOIN sizes s ON s.id = pv.size_id
JOIN attributes ac ON ac.tenant_id = pv.tenant_id AND ac.name = 'Color'
JOIN attribute_values avc ON avc.attribute_id = ac.id AND avc.tenant_id = pv.tenant_id AND avc.value = c.name
JOIN attributes at ON at.tenant_id = pv.tenant_id AND at.name = 'Talla'
JOIN attribute_values avt ON avt.attribute_id = at.id AND avt.tenant_id = pv.tenant_id AND avt.value = s.name
SET pv.variant_label = CONCAT(c.name, ' / ', s.name),
    pv.combination_hash = SHA2(CONCAT(LEAST(avc.id, avt.id), ',', GREATEST(avc.id, avt.id)), 256);
