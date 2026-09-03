package com.freestyleperu.aplicacion.tienda.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PublicProductoDetalleResponse(
        Long id,
        String name,
        String description,
        String material,
        String fit,
        BigDecimal price,
        BigDecimal promoPrice,
        String imageUrl,
        String sizeGuideImageUrl,
        String categoryName,
        String brandName,
        List<PublicVarianteResponse> variants,
        List<PublicProductImageResponse> images) {
}
