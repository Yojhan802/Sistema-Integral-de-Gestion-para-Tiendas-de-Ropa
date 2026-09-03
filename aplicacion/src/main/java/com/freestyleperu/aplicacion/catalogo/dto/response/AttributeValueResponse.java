package com.freestyleperu.aplicacion.catalogo.dto.response;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;

public record AttributeValueResponse(
        Long id,
        Long attributeId,
        String value,
        String hexCode,
        short sortOrder,
        EstadoGeneral status) {
}
