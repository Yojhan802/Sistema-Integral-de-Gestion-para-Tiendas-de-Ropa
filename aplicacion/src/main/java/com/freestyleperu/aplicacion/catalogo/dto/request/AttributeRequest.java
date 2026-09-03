package com.freestyleperu.aplicacion.catalogo.dto.request;

import com.freestyleperu.aplicacion.catalogo.domain.AttributeInputType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AttributeRequest(@NotBlank String name, @NotNull AttributeInputType inputType) {
}
