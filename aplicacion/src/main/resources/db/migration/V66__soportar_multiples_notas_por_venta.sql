-- Una venta puede necesitar más de una nota a lo largo de su ciclo fiscal.
-- La idempotencia sigue siendo la barrera contra duplicados.
ALTER TABLE electronic_documents
    DROP INDEX uk_electronic_document_tenant_sale_type;

CREATE INDEX idx_electronic_document_tenant_sale_type
    ON electronic_documents (tenant_id, sale_id, document_type);
