package com.freestyleperu.aplicacion.caja.repository;

import com.freestyleperu.aplicacion.caja.domain.CashSession;
import com.freestyleperu.aplicacion.caja.domain.CashSessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {

    boolean existsByCashRegisterIdAndStatus(Long cashRegisterId, CashSessionStatus status);

    @EntityGraph(attributePaths = { "cashRegister", "openedBy" })
    Optional<CashSession> findFirstByOpenedByIdAndStatusOrderByOpenedAtDesc(Long openedById, CashSessionStatus status);

    @EntityGraph(attributePaths = { "cashRegister", "openedBy", "closedBy" })
    @Query("""
            SELECT s FROM CashSession s
            WHERE (:cashRegisterId IS NULL OR s.cashRegister.id = :cashRegisterId)
              AND (:status IS NULL OR s.status = :status)
            ORDER BY s.openedAt DESC, s.id DESC
            """)
    Page<CashSession> buscar(@Param("cashRegisterId") Long cashRegisterId, @Param("status") CashSessionStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "cashRegister", "openedBy", "closedBy" })
    Optional<CashSession> findById(Long id);

    @EntityGraph(attributePaths = { "cashRegister", "openedBy", "closedBy" })
    @Query("SELECT s FROM CashSession s WHERE s.status = 'CLOSED' AND s.closedAt >= :from AND s.closedAt < :to ORDER BY s.closedAt DESC, s.id DESC")
    List<CashSession> sesionesCerradasEntre(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
