package com.freestyleperu.aplicacion.venta.repository;

import com.freestyleperu.aplicacion.venta.domain.Sale;
import com.freestyleperu.aplicacion.venta.domain.SaleStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = { "customer", "user" })
    @Query("""
            SELECT s FROM Sale s
            WHERE (:userId IS NULL OR s.user.id = :userId)
              AND (:customerId IS NULL OR s.customer.id = :customerId)
              AND (:status IS NULL OR s.status = :status)
              AND (:from IS NULL OR s.createdAt >= :from)
              AND (:to IS NULL OR s.createdAt <= :to)
            ORDER BY s.createdAt DESC, s.id DESC
            """)
    Page<Sale> buscar(
            @Param("userId") Long userId,
            @Param("customerId") Long customerId,
            @Param("status") SaleStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "customer", "user", "cashSession", "cancelledBy" })
    Optional<Sale> findById(Long id);

    /** Usado por la búsqueda global — coincidencia parcial de sale_number, más recientes primero. */
    @EntityGraph(attributePaths = { "customer", "user" })
    @Query("SELECT s FROM Sale s WHERE LOWER(s.saleNumber) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY s.createdAt DESC, s.id DESC")
    List<Sale> buscarPorNumero(@Param("query") String query, Pageable pageable);

    /** Una fila [count, total] de ventas completadas en el rango [from, to). Usado por los reportes. */
    @Query("SELECT COUNT(s), COALESCE(SUM(s.total), 0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.createdAt >= :from AND s.createdAt < :to")
    List<Object[]> resumen(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT FUNCTION('DATE', s.createdAt), COALESCE(SUM(s.total), 0)
            FROM Sale s
            WHERE s.status = 'COMPLETED' AND s.createdAt >= :from AND s.createdAt < :to
            GROUP BY FUNCTION('DATE', s.createdAt)
            ORDER BY FUNCTION('DATE', s.createdAt)
            """)
    List<Object[]> ventasPorDia(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
            SELECT s.user.fullName, COALESCE(SUM(s.total), 0)
            FROM Sale s
            WHERE s.status = 'COMPLETED' AND s.createdAt >= :from AND s.createdAt < :to
            GROUP BY s.user.id, s.user.fullName
            ORDER BY SUM(s.total) DESC
            """)
    List<Object[]> ventasPorVendedor(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Una fila [nombre del promotor, cantidad de ventas, total] por cada promotor con al menos una venta en el rango. */
    @Query("""
            SELECT s.promoter.name, COUNT(s), COALESCE(SUM(s.total), 0)
            FROM Sale s
            WHERE s.status = 'COMPLETED' AND s.promoter IS NOT NULL AND s.createdAt >= :from AND s.createdAt < :to
            GROUP BY s.promoter.id, s.promoter.name
            ORDER BY COUNT(s) DESC
            """)
    List<Object[]> ventasPorPromotor(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
