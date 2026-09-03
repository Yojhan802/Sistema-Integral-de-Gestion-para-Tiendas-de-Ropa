package com.freestyleperu.aplicacion.pago.port;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import java.util.Map;

/** Configuración resuelta para uso exclusivo del backend. */
public record PaymentProviderConfigurationData(
        PaymentProviderType provider,
        PaymentProviderEnvironment environment,
        String apiUrl,
        String merchantCode,
        String publicKey,
        Map<String, String> credentials) {
}
