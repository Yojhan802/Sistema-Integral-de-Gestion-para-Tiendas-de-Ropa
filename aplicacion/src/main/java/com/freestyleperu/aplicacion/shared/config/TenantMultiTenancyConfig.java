package com.freestyleperu.aplicacion.shared.config;

import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.util.Map;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra el resolver de tenant de Hibernate (conversión a SaaS multi-tenant, ver plan
 * aprobado) — el mecanismo que hace que {@code @TenantId} en las entidades filtre de verdad.
 * Verificado en un spike aislado (Fase 0 del plan) antes de aplicar esto en producción.
 *
 * <p>El resolver usa {@link TenantContext#DEFAULT_TENANT_ID} cuando el {@code ThreadLocal}
 * está vacío — a propósito, mientras {@code TenantResolutionFilter} (Fase 2 de la conversión)
 * todavía no exista, para que el comportamiento de un solo tenant no cambie. Una vez que ese
 * filtro fije el tenant en cada request, este default deja de usarse en la práctica (solo
 * seguiría aplicando a llamadas fuera de un request HTTP, como el scheduler de suscripciones,
 * que deben fijar {@link TenantContext} ellas mismas antes de tocar la base de datos).
 */
@Configuration
public class TenantMultiTenancyConfig {

    @Bean
    CurrentTenantIdentifierResolver<Long> tenantIdentifierResolver() {
        return new CurrentTenantIdentifierResolver<>() {
            @Override
            public Long resolveCurrentTenantIdentifier() {
                Long tenantId = TenantContext.get();
                return tenantId != null ? tenantId : TenantContext.DEFAULT_TENANT_ID;
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return true;
            }
        };
    }

    @Bean
    HibernatePropertiesCustomizer multiTenancyHibernatePropertiesCustomizer(CurrentTenantIdentifierResolver<Long> resolver) {
        return (Map<String, Object> properties) ->
                properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
    }
}
