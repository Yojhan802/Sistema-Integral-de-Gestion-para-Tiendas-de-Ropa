-- Cada tienda online necesita su propia cuenta técnica para atribuir los
-- movimientos de inventario que se generan al crear pedidos.
-- V37 solo creó esta cuenta para la empresa original, antes de la conversión
-- multi-tenant. Esta migración completa los tenants existentes sin duplicar
-- la cuenta que ya exista.
INSERT INTO users (
    tenant_id, username, email, password_hash, full_name, status,
    failed_attempts, locked_until, must_change_password, last_login_at,
    platform_operator, created_at, updated_at
)
SELECT cs.id,
       'sistema_tienda',
       NULL,
       '$2b$12$Ydly3s85ZGukAICzprfmPeqXUuXykKybCuMP15VwnLZfQHQxcBARG',
       'Sistema (Tienda Online)',
       'INACTIVE',
       0,
       NULL,
       FALSE,
       NULL,
       FALSE,
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6)
  FROM company_settings cs
  LEFT JOIN users u
    ON u.tenant_id = cs.id
   AND u.username = 'sistema_tienda'
 WHERE u.id IS NULL;
