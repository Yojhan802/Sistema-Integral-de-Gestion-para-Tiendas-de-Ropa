package com.freestyleperu.aplicacion.pago.dto.response;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentTransactionResponse(
        Long id,
        Long orderId,
        Long saleId,
        PaymentProviderType provider,
        BigDecimal amount,
        String currencyCode,
        PaymentTransactionStatus status,
        String providerTransactionId,
        String providerReference,
        String failureCode,
        String failureMessage,
        LocalDateTime expiresAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt) {
}
