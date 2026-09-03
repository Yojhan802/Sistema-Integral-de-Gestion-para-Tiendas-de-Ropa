package com.freestyleperu.aplicacion.reporte.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code datos} es el JSON del reporte que ya está en pantalla (el mismo que ya ve el usuario) — el asistente solo analiza eso, no recalcula nada. */
public record AsistenteReporteRequest(
        @NotBlank @Size(max = 500) String pregunta,
        @NotBlank @Size(max = 8000) String datos) {
}
