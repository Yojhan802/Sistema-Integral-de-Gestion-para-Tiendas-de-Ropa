package com.freestyleperu.aplicacion.producto.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ActualizarProductoImagenRequest(
        @Size(max = 150) String altText,
        @Min(0) Integer sortOrder) {
}
