package com.freestyleperu.aplicacion.plataforma.dto.response;

public record CrearTenantResponse(
        TenantResponse tenant,
        String ownerUsername,
        String temporaryPassword) {
}
