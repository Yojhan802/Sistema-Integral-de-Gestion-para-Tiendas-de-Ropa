package com.freestyleperu.aplicacion.producto.dto.response;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import java.util.List;

public record VarianteResponse(
        Long id,
        Long productId,
        String productName,
        List<VarianteAtributoResponse> attributes,
        String variantLabel,
        String sku,
        String barcode,
        int stock,
        int minStock,
        EstadoGeneral status) {
}
