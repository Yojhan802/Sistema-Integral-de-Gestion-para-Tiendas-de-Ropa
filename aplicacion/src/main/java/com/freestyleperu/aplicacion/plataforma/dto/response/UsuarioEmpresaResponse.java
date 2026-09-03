package com.freestyleperu.aplicacion.plataforma.dto.response;

/** Usuario de una empresa visto desde el panel del operador de plataforma. */
public record UsuarioEmpresaResponse(
        Long id,
        String username,
        String fullName,
        String status,
        /** Puede administrar empresas y módulos de toda la plataforma. */
        boolean platformOperator) {
}
