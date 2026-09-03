package com.freestyleperu.aplicacion.tienda.dto.response;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import java.time.LocalDateTime;

public record StorefrontBannerResponse(
        Long id,
        String imageUrl,
        String headline,
        String description,
        String ctaLabel,
        String ctaUrl,
        int sortOrder,
        EstadoGeneral status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
