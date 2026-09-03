package com.freestyleperu.aplicacion.pago.domain;

/** Estados internos de un intento de cobro online. */
public enum PaymentTransactionStatus {
    CREATED,
    PENDING,
    PROCESSING,
    APPROVED,
    DECLINED,
    FAILED,
    CANCELLED,
    REFUNDED
}
