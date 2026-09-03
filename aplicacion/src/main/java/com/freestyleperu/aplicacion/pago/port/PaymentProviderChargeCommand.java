package com.freestyleperu.aplicacion.pago.port;

import java.math.BigDecimal;

/** Datos calculados por el backend para solicitar un cargo; nunca incluye datos de tarjeta. */
public record PaymentProviderChargeCommand(
        Long transactionId,
        String orderNumber,
        BigDecimal amount,
        String currencyCode,
        String customerEmail,
        String customerName,
        String customerPhone,
        String customerAddress,
        String customerDocument,
        String sourceId) {
}
