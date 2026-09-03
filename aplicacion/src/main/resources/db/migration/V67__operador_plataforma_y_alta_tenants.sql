-- Marca a los usuarios internos de la plataforma. El flag no es un permiso del tenant:
-- permite distinguir al operador que administra empresas de un Administrador de tienda.
ALTER TABLE users
    ADD COLUMN platform_operator BOOLEAN NOT NULL DEFAULT FALSE AFTER tenant_id;

-- La cuenta admin existente es la cuenta del operador de la plataforma creada por V2.
UPDATE users
SET platform_operator = TRUE
WHERE tenant_id = 1
  AND username = 'admin';
