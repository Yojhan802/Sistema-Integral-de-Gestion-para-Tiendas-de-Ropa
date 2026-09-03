package com.freestyleperu.aplicacion.pedido.repository;

import com.freestyleperu.aplicacion.pedido.domain.Pedido;
import com.freestyleperu.aplicacion.pedido.domain.PedidoStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = { "customer" })
    @Query("""
            SELECT p FROM Pedido p
            WHERE (:customerId IS NULL OR p.customer.id = :customerId)
              AND (:status IS NULL OR p.status = :status)
              AND (:from IS NULL OR p.createdAt >= :from)
              AND (:to IS NULL OR p.createdAt <= :to)
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    Page<Pedido> buscar(
            @Param("customerId") Long customerId,
            @Param("status") PedidoStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "customer", "paymentMethod", "confirmedBy" })
    Optional<Pedido> findById(Long id);
}
