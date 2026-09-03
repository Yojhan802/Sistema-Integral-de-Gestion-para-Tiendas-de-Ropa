package com.freestyleperu.aplicacion.shared.audit;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId IS NULL OR a.userId = :userId)
              AND (:action IS NULL OR LOWER(a.action) LIKE LOWER(CONCAT('%', :action, '%')))
              AND (:entity IS NULL OR LOWER(a.entity) LIKE LOWER(CONCAT('%', :entity, '%')))
              AND (:result IS NULL OR a.result = :result)
              AND (:from IS NULL OR a.createdAt >= :from)
              AND (:to IS NULL OR a.createdAt <= :to)
            ORDER BY a.createdAt DESC, a.id DESC
            """)
    Page<AuditLog> buscar(
            @Param("userId") Long userId,
            @Param("action") String action,
            @Param("entity") String entity,
            @Param("result") AuditResult result,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
