package com.freestyleperu.aplicacion.catalogo.dto.response;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;

public record CategoryResponse(Long id, String name, String slug, String imageUrl, EstadoGeneral status) {
}
