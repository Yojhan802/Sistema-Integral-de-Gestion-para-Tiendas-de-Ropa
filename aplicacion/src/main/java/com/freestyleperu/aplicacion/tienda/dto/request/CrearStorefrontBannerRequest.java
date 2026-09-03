package com.freestyleperu.aplicacion.tienda.dto.request;

import jakarta.validation.constraints.Size;

public record CrearStorefrontBannerRequest(
        @Size(max = 255) String imageUrl,
        @Size(max = 150) String headline,
        @Size(max = 5000) String description,
        @Size(max = 80) String ctaLabel,
        @Size(max = 255) String ctaUrl,
        Integer sortOrder) {
}
