-- Conversión a SaaS multi-tenant (ver plan aprobado): company_settings deja de ser una fila
-- única (id=1) y pasa a ser la tabla de tenants — una fila por negocio. Este primer paso solo
-- prepara la tabla en sí: agrega el slug de subdominio y hace que el id sea autoincremental
-- (hoy se asigna a mano, siempre 1L).

-- V16 dejó un CHECK (id = 1) para forzar la fila única de entonces. Si no se quita aquí, ninguna
-- fila con id distinto de 1 podrá insertarse nunca — bloquearía por completo el resto de este plan.
ALTER TABLE company_settings
    DROP CONSTRAINT chk_company_settings_singleton;

ALTER TABLE company_settings
    ADD COLUMN slug VARCHAR(63) NULL AFTER name;

-- El negocio que ya existe (id=1) recibe un slug provisional — el operador de la plataforma lo
-- puede renombrar después vía UPDATE directo antes de anunciar el subdominio real al cliente.
UPDATE company_settings SET slug = 'default' WHERE id = 1 AND slug IS NULL;

ALTER TABLE company_settings
    MODIFY COLUMN slug VARCHAR(63) NOT NULL,
    ADD CONSTRAINT uk_company_settings_slug UNIQUE (slug);

-- MySQL exige que una columna con AUTO_INCREMENT sea (parte de) una clave — ya lo es (PRIMARY
-- KEY sobre id) — y que el próximo valor generado sea mayor que cualquiera ya existente (aquí,
-- 1), lo cual MySQL calcula solo al agregar AUTO_INCREMENT.
ALTER TABLE company_settings
    MODIFY COLUMN id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT;
