package com.freestyleperu.aplicacion.pago.port;

import java.math.BigDecimal;

/** Datos no sensibles necesarios para inicializar un formulario de checkout. */
public record PaymentProviderCheckoutCommand(
        Long transactionId,
        String orderNumber,
        BigDecimal amount,
        String currencyCode,
        String customerEmail,
        String customerName,
        String customerPhone,
        String customerAddress,
        String customerDocument) {
}
