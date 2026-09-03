package com.freestyleperu.aplicacion.plataforma.dto.request;

import jakarta.validation.constraints.NotNull;

/** Concede o retira a un usuario el acceso al módulo Empresas. */
public record CambiarOperadorRequest(@NotNull Boolean operador) {
}
