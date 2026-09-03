-- Sistema de atributos genéricos por producto: las columnas de snapshot color_name/size_name
-- en sale_details/order_details (decisión D-05: foto congelada al momento de la venta/pedido,
-- nunca se consulta por valor, ver Javadoc de SaleDetail/PedidoDetail) se reemplazan por una
-- sola variant_label — igual que ya existe product_name/variant_sku.

ALTER TABLE sale_details ADD COLUMN variant_label VARCHAR(150) NULL AFTER variant_sku;
UPDATE sale_details SET variant_label = CONCAT(color_name, ' / ', size_name) WHERE variant_label IS NULL;
ALTER TABLE sale_details
    MODIFY COLUMN variant_label VARCHAR(150) NOT NULL,
    DROP COLUMN color_name,
    DROP COLUMN size_name;

ALTER TABLE order_details ADD COLUMN variant_label VARCHAR(150) NULL AFTER variant_sku;
UPDATE order_details SET variant_label = CONCAT(color_name, ' / ', size_name) WHERE variant_label IS NULL;
ALTER TABLE order_details
    MODIFY COLUMN variant_label VARCHAR(150) NOT NULL,
    DROP COLUMN color_name,
    DROP COLUMN size_name;
