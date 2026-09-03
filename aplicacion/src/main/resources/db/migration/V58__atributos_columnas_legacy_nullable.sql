-- Sistema de atributos genéricos por producto: color_id/size_id pasan a ser nullable.
--
-- Ajuste sobre el plan original aprobado: el plan dejaba estas columnas NOT NULL como red de
-- seguridad hasta la limpieza final (Fase 5), asumiendo que solo hacía falta para no perder los
-- datos de ropa ya existentes. Pero al escribir el código real (Fase 2) se encontró que NOT NULL
-- también exige que TODA variante nueva de CUALQUIER producto siga teniendo color y talla —
-- exactamente lo que este cambio busca dejar de exigir. Los datos de ropa existentes no se
-- pierden (siguen con sus valores reales); solo se permite que una variante nueva de un rubro
-- sin color/talla (ej. "Voltaje") no los necesite.
ALTER TABLE product_variants
    MODIFY COLUMN color_id BIGINT UNSIGNED NULL,
    MODIFY COLUMN size_id BIGINT UNSIGNED NULL;
