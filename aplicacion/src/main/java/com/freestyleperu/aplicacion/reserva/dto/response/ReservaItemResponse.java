package com.freestyleperu.aplicacion.reserva.dto.response;

import java.math.BigDecimal;

public record ReservaItemResponse(
        Long variantId,
        String productName,
        String variantSku,
        String variantLabel,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal subtotal,
        Long comboId,
        String comboName,
        Integer comboGroup) {
}
