package com.freestyleperu.aplicacion.pago.dto.response;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import java.util.List;

public record PaymentProviderResponse(
        PaymentProviderType provider,
        boolean enabled,
        PaymentProviderEnvironment environment,
        String apiUrl,
        String merchantCode,
        String publicKey,
        boolean configured,
        List<String> credentialKeys) {
}
