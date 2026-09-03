-- Cuenta técnica "sistema_tienda": representa al sistema (no a un miembro del staff)
-- como responsable de los movimientos de inventario que dispara la tienda online sin
-- intervención de un cajero (ej. reserva de stock al crear un pedido). inventory_movements.user_id
-- es NOT NULL, así que hace falta un usuario real al que atribuir esos movimientos.
--
-- La cuenta queda INACTIVE (no puede iniciar sesión, ver AuthService.login()) y con un
-- password_hash aleatorio que nadie conoce, como segunda capa de protección: aunque alguien
-- lograra revertir el hash, el chequeo de estado ACTIVE en el login la sigue bloqueando.
INSERT INTO users (username, email, password_hash, full_name, status, must_change_password, created_at, updated_at)
VALUES (
    'sistema_tienda',
    NULL,
    '$2b$12$Ydly3s85ZGukAICzprfmPeqXUuXykKybCuMP15VwnLZfQHQxcBARG',
    'Sistema (Tienda Online)',
    'INACTIVE',
    FALSE,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
);
