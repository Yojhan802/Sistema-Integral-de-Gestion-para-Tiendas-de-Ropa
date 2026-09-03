package com.freestyleperu.aplicacion.tienda.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PublicProductoResumenResponse(
        Long id,
        String name,
        BigDecimal price,
        BigDecimal promoPrice,
        String imageUrl,
        String categoryName,
        String brandName,
        List<PublicColorSwatchResponse> colors,
        boolean inStock,
        List<PublicProductImageResponse> images) {
}
