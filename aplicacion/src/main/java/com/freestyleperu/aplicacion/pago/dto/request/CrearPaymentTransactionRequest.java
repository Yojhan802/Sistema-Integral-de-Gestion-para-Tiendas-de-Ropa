package com.freestyleperu.aplicacion.pago.dto.request;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import jakarta.validation.constraints.NotNull;

public record CrearPaymentTransactionRequest(@NotNull PaymentProviderType provider) {
}
