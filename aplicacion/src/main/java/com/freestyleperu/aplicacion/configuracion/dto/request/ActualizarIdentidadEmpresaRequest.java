package com.freestyleperu.aplicacion.configuracion.dto.request;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Razón social, RUC, dirección y contacto — gateado por CONFIGURACION_IDENTIDAD_EDITAR, reservado al operador de la plataforma (RN-26).
 * businessVertical/businessDescription alimentan el framing del asistente de IA (ver AsistenteTiendaService). */
public record ActualizarIdentidadEmpresaRequest(
        @NotBlank @Size(max = 150) String name,
        @Pattern(regexp = "^(?:\\d{11})?$", message = "debe contener exactamente 11 dígitos") @Size(max = 11) String ruc,
        @Size(max = 255) String address,
        @Size(max = 20) String phone,
        @Email @Size(max = 120) String email,
        @NotNull BusinessVertical businessVertical,
        @Size(max = 255) String businessDescription) {
}
