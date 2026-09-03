package com.freestyleperu.aplicacion.shared.security;

/**
 * Fuente única de verdad de "qué tenant es esta petición" (conversión a SaaS multi-tenant, ver
 * plan aprobado). Un {@code ThreadLocal} porque cada request HTTP se atiende en su propio hilo
 * de Tomcat, y este valor debe fijarse ANTES de que se abra cualquier transacción/sesión de
 * Hibernate — {@code TenantIdentifierResolver} lo lee para resolver el tenant de cada consulta,
 * y {@code JwtAuthenticationFilter}/controladores lo leen para el cruce de seguridad JWT↔subdominio.
 *
 * <p>En la Fase 1 de la conversión (esquema + anotaciones, sin exigir todavía), nada lo fija
 * aún — {@code TenantIdentifierResolver} usa {@code DEFAULT_TENANT_ID} cuando está vacío, así
 * que el comportamiento de un solo tenant se mantiene sin cambios hasta que
 * {@code TenantResolutionFilter} (Fase 2) empiece a fijarlo por request.
 */
public final class TenantContext {

    /** El tenant ya existente antes de esta conversión (fila `company_settings.id = 1`). */
    public static final Long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    /** Para código no invocado por una petición HTTP (ej. jobs @Scheduled) donde nada fijó el
     * contexto todavía — mismo fallback que usa el resolver de Hibernate. */
    public static Long getOrDefault() {
        Long current = CURRENT.get();
        return current != null ? current : DEFAULT_TENANT_ID;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
