package com.freestyleperu.aplicacion.pago.dto.request;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ActualizarPaymentProviderRequest(
        @NotNull Boolean enabled,
        @NotNull PaymentProviderEnvironment environment,
        @Size(max = 500) String apiUrl,
        @Size(max = 100) String merchantCode,
        @Size(max = 500) String publicKey,
        Map<String, String> credentials) {
}
