package com.freestyleperu.aplicacion.inventario.repository;

import com.freestyleperu.aplicacion.inventario.domain.InventoryMovement;
import com.freestyleperu.aplicacion.inventario.domain.MovementType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @EntityGraph(attributePaths = { "variant", "variant.product", "warehouse", "user" })
    @Query("""
            SELECT m FROM InventoryMovement m
            WHERE (:variantId IS NULL OR m.variant.id = :variantId)
              AND (:type IS NULL OR m.type = :type)
              AND (:from IS NULL OR m.createdAt >= :from)
              AND (:to IS NULL OR m.createdAt <= :to)
            ORDER BY m.createdAt DESC, m.id DESC
            """)
    Page<InventoryMovement> buscar(
            @Param("variantId") Long variantId,
            @Param("type") MovementType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM InventoryMovement m WHERE m.variant.id = :variantId")
    long sumaMovimientos(@Param("variantId") Long variantId);
}
