package com.freestyleperu.aplicacion.shared.security;

import java.util.Set;

public record AuthenticatedUser(Long id, String username, Set<String> authorities, Long tenantId) {

    public boolean tienePermiso(String permiso) {
        return authorities.contains(permiso);
    }
}
