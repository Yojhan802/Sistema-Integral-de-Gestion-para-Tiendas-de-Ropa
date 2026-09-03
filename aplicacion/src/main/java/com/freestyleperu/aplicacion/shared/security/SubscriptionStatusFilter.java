package com.freestyleperu.aplicacion.shared.security;

import com.freestyleperu.aplicacion.configuracion.PlanGate;
import com.freestyleperu.aplicacion.shared.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Corre antes que cualquier otra cosa (incluso antes de parsear el JWT) para
 * que, si la instalación está SUSPENDIDA por falta de pago (ver PlanGate,
 * SuscripcionScheduler, docs/03-modelo-datos.md §15), se bloquee absolutamente
 * todo — panel de staff y tienda pública por igual — con un único 402 claro,
 * en vez de depender de que cada controlador lo verifique.
 */
@Component
public class SubscriptionStatusFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_EXENTAS = Set.of(
            "/actuator/health", "/api/system/info", "/api/system/branding", "/api/system/subscription",
            "/api/auth/login", "/api/auth/refresh");

    private final PlanGate planGate;
    private final ObjectMapper objectMapper;

    public SubscriptionStatusFilter(PlanGate planGate, ObjectMapper objectMapper) {
        this.planGate = planGate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (RUTAS_EXENTAS.contains(request.getRequestURI())
                || request.getRequestURI().startsWith("/api/platform/")
                || planGate.suscripcionActiva()) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(402);
        response.setContentType("application/json;charset=UTF-8");
        ApiError error = ApiError.of(402, "SUBSCRIPTION_SUSPENDED",
                "El servicio está suspendido por falta de pago. Contacta a soporte para reactivarlo.",
                request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), error);
    }
}
