package com.freestyleperu.aplicacion.producto.dto.response;

import com.freestyleperu.aplicacion.catalogo.domain.AttributeInputType;

/** Un valor de atributo de la variante, en orden de exhibición (ver ProductAttribute.position). */
public record VarianteAtributoResponse(
        Long attributeId,
        String attributeName,
        AttributeInputType inputType,
        Long attributeValueId,
        String value,
        String hexCode) {
}
