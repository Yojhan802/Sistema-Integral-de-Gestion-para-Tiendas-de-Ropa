package com.freestyleperu.aplicacion.producto.repository;

import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    boolean existsByInternalCode(String internalCode);

    @Query("""
            SELECT p FROM Product p
            WHERE (:search IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(p.internalCode) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:subcategoryId IS NULL OR p.subcategory.id = :subcategoryId)
              AND (:brandId IS NULL OR p.brand.id = :brandId)
              AND (:status IS NULL OR p.status = :status)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """)
    Page<Product> buscar(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("subcategoryId") Long subcategoryId,
            @Param("brandId") Long brandId,
            @Param("status") EstadoGeneral status,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            LEFT JOIN p.category c
            LEFT JOIN p.brand b
            WHERE p.status = :status
              AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(b.name) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY p.name ASC
            """)
    java.util.List<Product> sugerencias(@Param("search") String search,
            @Param("status") EstadoGeneral status, Pageable pageable);
}
