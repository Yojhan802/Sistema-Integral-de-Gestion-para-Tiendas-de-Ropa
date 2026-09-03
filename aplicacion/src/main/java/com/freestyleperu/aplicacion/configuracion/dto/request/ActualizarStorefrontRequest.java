package com.freestyleperu.aplicacion.configuracion.dto.request;

import com.freestyleperu.aplicacion.configuracion.domain.StoreTemplate;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

/** Cambios publicados de la apariencia de la tienda; nunca contiene HTML o JavaScript. */
public record ActualizarStorefrontRequest(
        @NotNull StoreTemplate template,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColor,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String accentColor,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String backgroundColor) {
}
