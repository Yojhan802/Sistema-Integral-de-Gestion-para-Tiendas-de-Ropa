package com.freestyleperu.aplicacion.pago.repository;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderConfiguration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentProviderConfigurationRepository extends JpaRepository<PaymentProviderConfiguration, Long> {

    Optional<PaymentProviderConfiguration> findByProvider(PaymentProviderType provider);

    List<PaymentProviderConfiguration> findAllByOrderByProviderAsc();
}
