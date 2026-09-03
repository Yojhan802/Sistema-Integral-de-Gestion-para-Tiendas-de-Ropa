-- The administrator can change their own password from the panel.
-- Resetting another user's password remains a separate administrative action.
INSERT INTO permissions (code, module, description)
VALUES ('USUARIOS_CAMBIAR_CONTRASENA', 'USUARIOS', 'Cambiar la contrasena propia')
ON DUPLICATE KEY UPDATE description = VALUES(description), module = VALUES(module);

-- Only the Administrator role receives this permission by default.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  JOIN permissions p ON p.code = 'USUARIOS_CAMBIAR_CONTRASENA'
 WHERE r.code = 'ADMINISTRADOR';
