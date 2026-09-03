package com.freestyleperu.aplicacion.pago.port;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;

/** Resultado seguro para que el navegador monte el checkout del proveedor. */
public record PaymentProviderCheckoutResult(
        PaymentProviderType provider,
        String sessionToken,
        String scriptUrl,
        String merchantCode,
        String correlationId,
        String publicKey,
        int expirationMinutes) {
}
