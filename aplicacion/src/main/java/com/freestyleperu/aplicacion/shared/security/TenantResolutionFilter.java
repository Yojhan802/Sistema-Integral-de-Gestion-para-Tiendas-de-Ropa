package com.freestyleperu.aplicacion.shared.security;

import com.freestyleperu.aplicacion.shared.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Corre PRIMERO en toda la cadena de seguridad (antes que OpsApiKeyAuthenticationFilter,
 * SubscriptionStatusFilter y JwtAuthenticationFilter, ver SecurityConfig) — resuelve a qué
 * negocio (tenant) pertenece esta petición y fija {@link TenantContext} antes de que se abra
 * cualquier transacción/sesión de Hibernate (ver Javadoc de TenantContext: el resolver se
 * consulta al ABRIR la sesión, no en cada query — fijarlo después sería demasiado tarde).
 *
 * <p>En producción, el negocio se resuelve por subdominio (ej. {@code tiendax.qynex.pe} →
 * slug {@code tiendax}) — si no hay ningún tenant con ese slug, 404 directo, nunca un default
 * silencioso. Fuera de producción (dev/test) no existe DNS comodín real, así que se admite un
 * header de desarrollo {@code X-Tenant-Slug} para simular un subdominio, y si no viene se cae al
 * tenant por defecto de la Fase 1 ({@link TenantContext#DEFAULT_TENANT_ID}) — así el resto de la
 * suite de tests y el flujo manual de desarrollo existentes siguen funcionando sin tocarlos.
 *
 * <p>{@code app.tenant.strict-subdomain-resolution} (default {@code true}) es un escape hatch
 * deliberadamente separado del perfil de Spring: un despliegue puede necesitar el tamaño de
 * pool/hilos del perfil "prod" (ver application.yml) mientras todavía no tiene un subdominio real
 * configurado (ej. una demo servida por IP/localhost antes de tener dominio) — sin este flag,
 * esa combinación no tendría forma de funcionar sin degradar la resolución estricta para
 * despliegues reales que sí tienen subdominio. Default {@code true} preserva el comportamiento
 * estricto de siempre; hay que apagarlo a propósito.
 */
@Component
public class TenantResolutionFilter extends OncePerRequestFilter {

    /** Nunca requieren un tenant resuelto: infraestructura, y el endpoint de operador — ese ya
     * recibe el tenantId explícito en el body (ver ActualizarSuscripcionRequest), no por subdominio. */
    private static final Set<String> RUTAS_EXACTAS_EXENTAS = Set.of("/actuator/health", "/api/system/subscription");

    private static final String HEADER_DEV_SLUG = "X-Tenant-Slug";

    private final TenantSlugResolver tenantSlugResolver;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final boolean strictSubdomainResolution;

    public TenantResolutionFilter(TenantSlugResolver tenantSlugResolver, Environment environment, ObjectMapper objectMapper,
            @Value("${app.tenant.strict-subdomain-resolution:true}") boolean strictSubdomainResolution) {
        this.tenantSlugResolver = tenantSlugResolver;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.strictSubdomainResolution = strictSubdomainResolution;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (RUTAS_EXACTAS_EXENTAS.contains(request.getRequestURI())
                || request.getRequestURI().startsWith("/api/platform/")) {
            chain.doFilter(request, response);
            return;
        }

        boolean permiteOverrideDeDesarrollo = !environment.matchesProfiles("prod") || !strictSubdomainResolution;
        String slugHeader = permiteOverrideDeDesarrollo ? request.getHeader(HEADER_DEV_SLUG) : null;

        Long tenantId;
        if (slugHeader != null && !slugHeader.isBlank()) {
            Optional<Long> resuelto = tenantSlugResolver.resolver(slugHeader);
            if (resuelto.isEmpty()) {
                escribirNoEncontrado(response, request, slugHeader);
                return;
            }
            tenantId = resuelto.get();
        } else if (permiteOverrideDeDesarrollo) {
            // dev/test sin header explícito: comportamiento de la Fase 1, tenant por defecto.
            String slugLocal = extraerSubdominio(request.getServerName());
            tenantId = slugLocal != null
                    ? tenantSlugResolver.resolver(slugLocal).orElse(TenantContext.DEFAULT_TENANT_ID)
                    : TenantContext.DEFAULT_TENANT_ID;
        } else {
            String slug = extraerSubdominio(request.getServerName());
            Optional<Long> resuelto = slug != null ? tenantSlugResolver.resolver(slug) : Optional.empty();
            if (resuelto.isEmpty()) {
                escribirNoEncontrado(response, request, slug);
                return;
            }
            tenantId = resuelto.get();
        }

        TenantContext.set(tenantId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** {@code tiendax.qynex.pe} → {@code "tiendax"}; el apex ({@code qynex.pe}, 2 etiquetas) o un host sin puntos no es un subdominio de negocio válido. */
    private String extraerSubdominio(String host) {
        String normalizado = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalizado.endsWith(".localhost")) {
            String slugLocal = normalizado.substring(0, normalizado.length() - ".localhost".length());
            return slugLocal.matches("[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])?") ? slugLocal : null;
        }
        String[] etiquetas = normalizado.split("\\.");
        return etiquetas.length >= 3 ? etiquetas[0] : null;
    }

    private void escribirNoEncontrado(HttpServletResponse response, HttpServletRequest request, String slug) throws IOException {
        response.setStatus(404);
        response.setContentType("application/json;charset=UTF-8");
        String mensaje = slug != null
                ? "No existe ningún negocio con el subdominio '" + slug + "'"
                : "No se pudo determinar el negocio a partir del dominio de esta petición";
        ApiError error = ApiError.of(404, "TENANT_NOT_FOUND", mensaje, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), error);
    }
}
