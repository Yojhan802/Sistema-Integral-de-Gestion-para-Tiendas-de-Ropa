package com.freestyleperu.aplicacion.plataforma.repository;

import com.freestyleperu.aplicacion.plataforma.domain.SubscriptionPayment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    /** El id desempata: dos cobros del mismo instante deben salir en orden de registro. */
    List<SubscriptionPayment> findAllByTenantIdOrderByPaidAtDescIdDesc(Long tenantId, Pageable pageable);

    /** Lo efectivamente cobrado en un rango, para contrastarlo con lo facturable. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM SubscriptionPayment p WHERE p.paidAt >= :desde AND p.paidAt < :hasta")
    Optional<BigDecimal> totalCobradoEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
