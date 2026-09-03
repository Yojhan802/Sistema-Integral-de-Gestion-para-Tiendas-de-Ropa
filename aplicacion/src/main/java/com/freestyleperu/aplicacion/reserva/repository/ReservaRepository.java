package com.freestyleperu.aplicacion.reserva.repository;

import com.freestyleperu.aplicacion.reserva.domain.Reserva;
import com.freestyleperu.aplicacion.reserva.domain.ReservaStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /**
     * {@code buyerName} busca por nombre tanto en clientes registrados como en
     * compradores ocasionales (RN-27) — para ubicar rápido a alguien que llega
     * a recoger en persona, sin importar si se registró como cliente o no.
     */
    // "details.variant.attributeValues..." NO se agrega acá a propósito: es una colección
    // (List) igual que "details" mismo, y Hibernate no permite fetch-join simultáneo de dos
    // colecciones tipo List (MultipleBagFetchException) — pero tampoco hace falta: variant.
    // variantLabel ya es una columna plana, no requiere cargar attributeValues.
    @EntityGraph(attributePaths = {
            "customer", "details", "details.variant", "details.variant.product", "details.combo", "promoter" })
    @Query("""
            SELECT r FROM Reserva r
            LEFT JOIN r.customer c
            WHERE (:status IS NULL OR r.status = :status)
              AND (:customerId IS NULL OR r.customer.id = :customerId)
              AND (:buyerName IS NULL
                   OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :buyerName, '%'))
                   OR LOWER(r.guestName) LIKE LOWER(CONCAT('%', :buyerName, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<Reserva> buscar(
            @Param("status") ReservaStatus status, @Param("customerId") Long customerId,
            @Param("buyerName") String buyerName, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "customer", "details", "details.variant", "details.variant.product", "details.combo", "promoter", "createdBy" })
    Optional<Reserva> findById(Long id);

    /** Usado por ReservaScheduler para vencer separaciones cuyo plazo ya pasó. */
    List<Reserva> findAllByStatusAndExpiresAtBefore(ReservaStatus status, LocalDateTime moment);
}
