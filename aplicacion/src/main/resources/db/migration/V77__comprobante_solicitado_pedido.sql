-- El checkout online conserva la elección del comprador para que el negocio
-- pueda emitir el comprobante correcto cuando el pedido se confirma.
-- TICKET es una constancia interna y solo aplica cuando no hay facturación
-- electrónica; BOLETA/FACTURA se procesan por el proveedor configurado.

ALTER TABLE orders
    ADD COLUMN billing_document_type VARCHAR(20) NOT NULL DEFAULT 'TICKET' AFTER payment_proof_url,
    ADD COLUMN billing_document_number VARCHAR(15) NULL AFTER billing_document_type,
    ADD COLUMN billing_name VARCHAR(150) NULL AFTER billing_document_number,
    ADD CONSTRAINT chk_orders_billing_document_type
        CHECK (billing_document_type IN ('TICKET', 'BOLETA', 'FACTURA'));

ALTER TABLE sales
    ADD COLUMN billing_document_type VARCHAR(20) NOT NULL DEFAULT 'TICKET' AFTER shipping_amount,
    ADD COLUMN billing_document_number VARCHAR(15) NULL AFTER billing_document_type,
    ADD COLUMN billing_name VARCHAR(150) NULL AFTER billing_document_number,
    ADD CONSTRAINT chk_sales_billing_document_type
        CHECK (billing_document_type IN ('TICKET', 'BOLETA', 'FACTURA'));
