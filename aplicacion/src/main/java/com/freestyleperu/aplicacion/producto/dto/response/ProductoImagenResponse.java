package com.freestyleperu.aplicacion.producto.dto.response;

public record ProductoImagenResponse(
        Long id,
        Long productId,
        String imageUrl,
        String altText,
        int sortOrder,
        boolean primary) {
}
