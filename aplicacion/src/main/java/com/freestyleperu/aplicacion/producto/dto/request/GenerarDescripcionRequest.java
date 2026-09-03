package com.freestyleperu.aplicacion.producto.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos que el usuario ya escribió en el formulario — el asistente redacta la descripción solo a partir de esto, sin inventar atributos que no se le dieron. */
public record GenerarDescripcionRequest(
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 100) String categoria,
        @Size(max = 100) String marca,
        @Size(max = 100) String material,
        @Size(max = 100) String calce) {
}
