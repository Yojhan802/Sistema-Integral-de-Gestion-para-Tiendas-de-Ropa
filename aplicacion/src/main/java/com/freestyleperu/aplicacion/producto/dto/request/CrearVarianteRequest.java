package com.freestyleperu.aplicacion.producto.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CrearVarianteRequest(
        @NotEmpty List<Long> attributeValueIds,
        String sku,
        String barcode,
        @Min(0) Integer stock,
        @Min(0) Integer minStock,
        boolean generateBarcode) {
}
