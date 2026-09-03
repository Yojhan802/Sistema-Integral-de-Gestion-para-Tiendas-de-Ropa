-- Changing one's own password is available to every role.
INSERT INTO permissions (code, module, description)
VALUES ('USUARIOS_RESETEAR_CONTRASENA', 'USUARIOS', 'Resetear la contraseña de otro usuario')
ON DUPLICATE KEY UPDATE description = VALUES(description), module = VALUES(module);

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code = 'USUARIOS_CAMBIAR_CONTRASENA'
 WHERE r.code IS NOT NULL;

-- Resetting another user's password is reserved for Administrador.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code = 'USUARIOS_RESETEAR_CONTRASENA'
 WHERE r.code = 'ADMINISTRADOR';
