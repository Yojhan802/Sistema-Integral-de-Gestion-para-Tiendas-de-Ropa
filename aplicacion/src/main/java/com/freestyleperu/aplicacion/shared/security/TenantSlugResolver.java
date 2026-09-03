package com.freestyleperu.aplicacion.shared.security;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Slug de subdominio → tenant id (ver TenantResolutionFilter). Corre en cada petición, así que
 * se cachea unos segundos (comparte el spec de Caffeine ya usado por el catálogo público de la
 * tienda, ver application.yml `spring.cache.caffeine.spec`) — no golpea la base por cada request.
 */
@Service
public class TenantSlugResolver {

    private final CompanySettingsRepository companySettingsRepository;

    public TenantSlugResolver(CompanySettingsRepository companySettingsRepository) {
        this.companySettingsRepository = companySettingsRepository;
    }

    @Cacheable("tenantSlugLookup")
    public Optional<Long> resolver(String slug) {
        return companySettingsRepository.findBySlug(slug).map(CompanySettings::getId);
    }
}
