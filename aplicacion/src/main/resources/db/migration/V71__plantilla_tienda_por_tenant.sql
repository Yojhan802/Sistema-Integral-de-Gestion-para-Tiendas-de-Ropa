-- Plantilla visual por tenant. El catalogo, checkout y pedidos siguen siendo compartidos.
ALTER TABLE company_settings
    ADD COLUMN store_template VARCHAR(30) NOT NULL DEFAULT 'CLASSIC' AFTER logo_url,
    ADD CONSTRAINT chk_company_settings_store_template CHECK (
        store_template IN ('CLASSIC', 'MINIMAL', 'FASHION', 'SPORT', 'LUXURY',
                           'BOUTIQUE', 'CATALOG', 'MARKET', 'EDITORIAL', 'URBAN')
    );
