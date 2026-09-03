package com.freestyleperu.aplicacion.tienda.dto.response;

import java.util.List;

public record PublicVarianteResponse(
        Long variantId,
        String variantLabel,
        List<PublicAttributeValueResponse> attributes,
        boolean inStock) {
}
