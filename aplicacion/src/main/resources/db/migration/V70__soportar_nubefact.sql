-- Permite seleccionar NubeFact como proveedor de facturación por tenant.
-- Los documentos existentes conservan VERIFACT y no se migran de proveedor.

ALTER TABLE billing_configurations
    DROP CHECK chk_billing_config_provider;

ALTER TABLE billing_configurations
    ADD CONSTRAINT chk_billing_config_provider
        CHECK (provider IN ('VERIFACT', 'NUBEFACT'));

ALTER TABLE electronic_documents
    DROP CHECK chk_electronic_document_provider;

ALTER TABLE electronic_documents
    ADD CONSTRAINT chk_electronic_document_provider
        CHECK (provider IN ('VERIFACT', 'NUBEFACT'));
