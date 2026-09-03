package com.freestyleperu.aplicacion.facturacion.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record NotaItemRequest(
        @NotNull Long variantId,
        @NotNull @Min(1) Integer quantity) {
}
