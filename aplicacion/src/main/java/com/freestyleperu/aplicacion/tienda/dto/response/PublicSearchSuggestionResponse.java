package com.freestyleperu.aplicacion.tienda.dto.response;

public record PublicSearchSuggestionResponse(
        String type,
        Long id,
        String title,
        String subtitle,
        String imageUrl) {
}
