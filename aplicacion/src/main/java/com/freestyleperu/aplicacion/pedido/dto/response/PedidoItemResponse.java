package com.freestyleperu.aplicacion.pedido.dto.response;

import java.math.BigDecimal;

public record PedidoItemResponse(
        Long variantId,
        String productName,
        String variantSku,
        String variantLabel,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal) {
}
