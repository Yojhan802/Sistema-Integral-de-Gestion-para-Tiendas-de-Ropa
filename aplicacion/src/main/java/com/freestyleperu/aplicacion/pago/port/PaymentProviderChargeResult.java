package com.freestyleperu.aplicacion.pago.port;

import com.freestyleperu.aplicacion.pago.domain.PaymentTransactionStatus;

public record PaymentProviderChargeResult(
        PaymentTransactionStatus status,
        String providerTransactionId,
        String providerReference,
        String failureCode,
        String failureMessage) {
}
