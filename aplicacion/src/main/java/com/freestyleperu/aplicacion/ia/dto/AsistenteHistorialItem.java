package com.freestyleperu.aplicacion.ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Un turno previo de la conversación, tal como lo tiene el widget en pantalla. */
public record AsistenteHistorialItem(
        @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 800) String content) {
}
