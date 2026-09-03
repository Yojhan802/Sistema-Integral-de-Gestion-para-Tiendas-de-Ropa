-- Conversión a SaaS multi-tenant: agrega tenant_id a las tablas de usuarios/seguridad/auditoría.
-- Nullable por ahora a propósito — el backfill (V4x) lo llena antes de que otra migración lo
-- vuelva NOT NULL. role_permissions y user_roles (tablas de unión puras, sin @Entity propia)
-- quedan sin tenant_id: sus filas ya están acotadas a un tenant transitivamente por role_id/
-- user_id, que sí son de roles/usuarios ya aislados por tenant.

ALTER TABLE users
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE roles
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE refresh_tokens
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_refresh_tokens_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);

ALTER TABLE audit_logs
    ADD COLUMN tenant_id BIGINT UNSIGNED NULL AFTER id,
    ADD CONSTRAINT fk_audit_logs_tenant FOREIGN KEY (tenant_id) REFERENCES company_settings (id);
