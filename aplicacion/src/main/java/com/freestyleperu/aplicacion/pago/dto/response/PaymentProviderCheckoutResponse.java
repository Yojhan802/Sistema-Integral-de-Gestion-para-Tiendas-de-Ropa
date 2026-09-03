package com.freestyleperu.aplicacion.pago.dto.response;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;

/** Respuesta pública para montar el formulario del proveedor en el navegador. */
public record PaymentProviderCheckoutResponse(
        PaymentProviderType provider,
        String sessionToken,
        String scriptUrl,
        String merchantCode,
        String correlationId,
        String publicKey,
        String purchaseNumber,
        String amount,
        String currencyCode,
        int expirationMinutes) {
}
