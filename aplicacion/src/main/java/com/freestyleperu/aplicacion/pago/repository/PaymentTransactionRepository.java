package com.freestyleperu.aplicacion.pago.repository;

import com.freestyleperu.aplicacion.pago.domain.PaymentTransaction;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @EntityGraph(attributePaths = { "order", "sale" })
    Optional<PaymentTransaction> findById(Long id);

    @EntityGraph(attributePaths = { "order", "sale" })
    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findFirstByProviderAndProviderTransactionId(
            PaymentProviderType provider, String providerTransactionId);

    @Query("""
            select t.provider, t.status, count(t), coalesce(sum(t.amount), 0)
            from PaymentTransaction t
            where t.createdAt >= :from and t.createdAt < :to
            group by t.provider, t.status
            order by t.provider, t.status
            """)
    List<Object[]> resumenPorEstado(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
