package com.freestyleperu.aplicacion.catalogo.dto.response;

import com.freestyleperu.aplicacion.catalogo.domain.AttributeInputType;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import java.util.List;

public record AttributeResponse(
        Long id,
        String name,
        AttributeInputType inputType,
        EstadoGeneral status,
        List<AttributeValueResponse> values) {
}
