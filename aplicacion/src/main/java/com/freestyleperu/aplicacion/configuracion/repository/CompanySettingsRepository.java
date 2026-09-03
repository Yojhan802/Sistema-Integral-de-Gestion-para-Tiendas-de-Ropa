package com.freestyleperu.aplicacion.configuracion.repository;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {

    /** Usado por TenantResolutionFilter para resolver el tenant a partir del subdominio. */
    Optional<CompanySettings> findBySlug(String slug);
}
