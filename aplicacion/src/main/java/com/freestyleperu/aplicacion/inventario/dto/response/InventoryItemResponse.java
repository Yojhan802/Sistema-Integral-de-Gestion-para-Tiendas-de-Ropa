package com.freestyleperu.aplicacion.inventario.dto.response;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;

public record InventoryItemResponse(
        Long variantId,
        String productName,
        String sku,
        String barcode,
        String variantLabel,
        int stock,
        int minStock,
        EstadoGeneral status) {
}
