package com.freestyleperu.aplicacion.producto.repository;

import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    String GRAFO_VARIANTE = "attributeValues.attributeValue.attribute";

    @EntityGraph(attributePaths = { "product", GRAFO_VARIANTE })
    List<ProductVariant> findAllByProductId(Long productId);

    @EntityGraph(attributePaths = { "product", GRAFO_VARIANTE })
    @Query("""
            SELECT v FROM ProductVariant v
            WHERE (:search IS NULL
                   OR LOWER(v.product.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(v.sku) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(v.barcode) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<ProductVariant> buscarInventario(@Param("search") String search, Pageable pageable);

    @EntityGraph(attributePaths = { "product", GRAFO_VARIANTE })
    @Query("SELECT v FROM ProductVariant v WHERE v.stock <= v.minStock AND v.status = 'ACTIVE' ORDER BY v.stock ASC")
    List<ProductVariant> findLowStock();

    @EntityGraph(attributePaths = { "product", GRAFO_VARIANTE })
    @Query("SELECT v FROM ProductVariant v WHERE v.stock = 0 AND v.status = 'ACTIVE' ORDER BY v.updatedAt DESC")
    List<ProductVariant> findOutOfStock();

    boolean existsByProductIdAndCombinationHash(Long productId, String combinationHash);

    boolean existsByBarcode(String barcode);

    boolean existsBySku(String sku);

    @EntityGraph(attributePaths = { "product", GRAFO_VARIANTE })
    Optional<ProductVariant> findByBarcode(String barcode);

    @EntityGraph(attributePaths = { "product", GRAFO_VARIANTE })
    @Query("""
            SELECT v FROM ProductVariant v
            WHERE LOWER(v.sku) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(v.barcode) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(v.product.name) LIKE LOWER(CONCAT('%', :query, '%'))
            """)
    List<ProductVariant> buscar(@Param("query") String query);
}
