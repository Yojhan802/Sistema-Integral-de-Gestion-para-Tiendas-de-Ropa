package com.freestyleperu.aplicacion.producto.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Genera el producto cartesiano N-ario de {@code attributeValueIdGroups} — una lista por
 * atributo elegido (ej. lista de colores, lista de tallas), en el orden en que se deban
 * asignar las posiciones si el producto todavía no tiene esos atributos configurados.
 */
public record GenerarVariantesRequest(
        @NotEmpty List<@NotEmpty List<Long>> attributeValueIdGroups,
        @Min(0) Integer minStock,
        boolean generateBarcodes) {
}
