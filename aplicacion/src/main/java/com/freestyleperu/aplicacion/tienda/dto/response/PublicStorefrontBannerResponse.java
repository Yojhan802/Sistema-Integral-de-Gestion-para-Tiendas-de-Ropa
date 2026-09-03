package com.freestyleperu.aplicacion.tienda.dto.response;

public record PublicStorefrontBannerResponse(
        Long id,
        String imageUrl,
        String headline,
        String description,
        String ctaLabel,
        String ctaUrl) {
}
