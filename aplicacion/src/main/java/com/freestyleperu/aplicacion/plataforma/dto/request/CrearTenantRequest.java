package com.freestyleperu.aplicacion.plataforma.dto.request;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.freestyleperu.aplicacion.plataforma.dto.request.ActualizarModulosRequest.ModuloSeleccionado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CrearTenantRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(min = 3, max = 63)
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?$",
                message = "solo admite minúsculas, números y guiones") String slug,
        @Pattern(regexp = "^(?:\\d{11})?$", message = "debe contener exactamente 11 dígitos") @Size(max = 11) String ruc,
        @Size(max = 255) String address,
        @Size(max = 20) String phone,
        @Email @Size(max = 120) String email,
        @NotNull BusinessVertical businessVertical,
        @NotNull Plan plan,
        LocalDate nextPaymentDue,
        @NotBlank @Size(min = 4, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "solo admite letras, números, punto, guion y guion bajo")
        String ownerUsername,
        @Email @Size(max = 120) String ownerEmail,
        @NotBlank @Size(max = 120) String ownerFullName,

        /**
         * Paquete contratado. Si viene vacío se siembran los módulos del plan elegido, que
         * es el comportamiento esperado cuando se vende un plan sin ajustes.
         */
        @Valid List<ModuloSeleccionado> modulos,

        /**
         * Costo de implementación, que cubre el primer mes. Se registra como el primer pago
         * de la empresa para que el historial nazca completo. Si viene vacío se toma el
         * total del paquete contratado.
         */
        @DecimalMin(value = "0.00", message = "no puede ser negativo") BigDecimal costoImplementacion,

        /** Falso para la tienda propia o el demo: no suman al ingreso mensual. */
        Boolean billable) {

    public boolean esFacturable() {
        return billable == null || billable;
    }

    /** Compatibilidad con altas anteriores a la venta por módulos. */
    public CrearTenantRequest(String name, String slug, String ruc, String address, String phone, String email,
            BusinessVertical businessVertical, Plan plan, LocalDate nextPaymentDue, String ownerUsername,
            String ownerEmail, String ownerFullName) {
        this(name, slug, ruc, address, phone, email, businessVertical, plan, nextPaymentDue,
                ownerUsername, ownerEmail, ownerFullName, List.of(), null, null);
    }
}
