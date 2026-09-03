package com.freestyleperu.aplicacion.catalogo.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AttributeValueRequest(@NotBlank String value, String hexCode, short sortOrder) {
}
