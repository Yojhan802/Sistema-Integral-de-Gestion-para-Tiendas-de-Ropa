-- Verifac asigna el correlativo al emitir: su API recibe la serie, no el número.
-- El ledger mantiene el número vacío mientras el documento está en borrador.
ALTER TABLE electronic_documents
    MODIFY COLUMN document_number VARCHAR(20) NULL;
