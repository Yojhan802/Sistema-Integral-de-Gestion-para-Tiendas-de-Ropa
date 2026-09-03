package com.freestyleperu.aplicacion.configuracion.dto.request;

import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * {@code nextPaymentDue} es opcional: si se omite, se deja la fecha actual
 * sin tocar (ej. al suspender manualmente no siempre hace falta cambiarla).
 * {@code tenantId} identifica explícitamente el negocio a actualizar — esta
 * ruta está exenta de TenantResolutionFilter (el panel externo de monitoreo
 * no llega por subdominio), así que no hay contexto ambiental de tenant.
 * Ver OpsApiKeyAuthenticationFilter — este endpoint no usa login de usuario.
 */
public record ActualizarSuscripcionRequest(
        @NotNull Long tenantId, @NotNull SubscriptionStatus subscriptionStatus, LocalDate nextPaymentDue) {
}
