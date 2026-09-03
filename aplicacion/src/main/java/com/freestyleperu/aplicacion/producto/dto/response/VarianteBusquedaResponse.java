package com.freestyleperu.aplicacion.producto.dto.response;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import java.math.BigDecimal;

/** Respuesta optimizada para el escaneo/búsqueda en el POS (docs/05-api.md §6). */
public record VarianteBusquedaResponse(
        Long variantId,
        String productName,
        String variantLabel,
        String sku,
        String barcode,
        BigDecimal price,
        BigDecimal promoPrice,
        BigDecimal effectivePrice,
        int stock,
        EstadoGeneral status) {
}
