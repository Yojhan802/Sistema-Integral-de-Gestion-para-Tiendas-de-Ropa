package com.freestyleperu.aplicacion.pago.dto.response;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;

public record PublicPaymentProviderResponse(
        PaymentProviderType provider,
        PaymentProviderEnvironment environment,
        String apiUrl,
        String merchantCode,
        String publicKey) {
}
