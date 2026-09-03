package com.freestyleperu.aplicacion.tienda.dto.response;

public record PublicProductImageResponse(
        Long id,
        String imageUrl,
        String altText,
        int sortOrder,
        boolean primary) {
}
