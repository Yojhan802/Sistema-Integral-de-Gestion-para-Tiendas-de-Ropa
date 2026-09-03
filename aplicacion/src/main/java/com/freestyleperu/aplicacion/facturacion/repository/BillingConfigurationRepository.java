package com.freestyleperu.aplicacion.facturacion.repository;

import com.freestyleperu.aplicacion.facturacion.domain.BillingConfiguration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingConfigurationRepository extends JpaRepository<BillingConfiguration, Long> {

    Optional<BillingConfiguration> findFirstByOrderByIdAsc();
}
