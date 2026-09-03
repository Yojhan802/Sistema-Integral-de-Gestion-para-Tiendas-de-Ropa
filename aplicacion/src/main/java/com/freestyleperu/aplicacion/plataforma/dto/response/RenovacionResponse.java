package com.freestyleperu.aplicacion.plataforma.dto.response;

import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import java.time.LocalDate;

/**
 * Resultado de renovar. Devuelve el estado resultante para que el panel muestre de
 * inmediato hasta cuándo quedó cubierta la empresa y si se reactivó.
 */
public record RenovacionResponse(
        Long tenantId,
        LocalDate nextPaymentDue,
        SubscriptionStatus subscriptionStatus,
        boolean reactivada,
        PagoSuscripcionResponse pago) {
}
