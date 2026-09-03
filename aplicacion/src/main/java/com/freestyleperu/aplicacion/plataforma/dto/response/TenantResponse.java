package com.freestyleperu.aplicacion.plataforma.dto.response;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TenantResponse(
        Long id,
        String slug,
        String name,
        String ruc,
        String address,
        String phone,
        String email,
        BusinessVertical businessVertical,
        Plan plan,
        SubscriptionStatus subscriptionStatus,
        /** Falso para la tienda propia o el demo: no suman al ingreso. */
        boolean billable,
        LocalDate nextPaymentDue,
        String ownerUsername,
        int activeUsers,
        /** Suma de los modulos contratados; es lo que factura esta empresa al mes. */
        BigDecimal monthlyTotal,
        int moduleCount,
        LocalDateTime updatedAt) {
}
