package com.freestyleperu.aplicacion.plataforma.dto.request;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ActualizarTenantRequest(
        @NotBlank @Size(max = 150) String name,
        @Pattern(regexp = "^(?:\\d{11})?$", message = "debe contener exactamente 11 dígitos") @Size(max = 11) String ruc,
        @Size(max = 255) String address,
        @Size(max = 20) String phone,
        @Email @Size(max = 120) String email,
        @NotNull BusinessVertical businessVertical,
        @NotNull Plan plan,
        @NotNull SubscriptionStatus subscriptionStatus,
        LocalDate nextPaymentDue,
        /** Nulo = se conserva lo que ya tenía; así un cliente antiguo no cambia por omisión. */
        Boolean billable) {

    public boolean facturableODefecto(boolean actual) {
        return billable == null ? actual : billable;
    }
}
