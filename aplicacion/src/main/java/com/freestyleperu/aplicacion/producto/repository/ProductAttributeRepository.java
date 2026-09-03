package com.freestyleperu.aplicacion.producto.repository;

import com.freestyleperu.aplicacion.producto.domain.ProductAttribute;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    @EntityGraph(attributePaths = "attribute")
    List<ProductAttribute> findAllByProductIdOrderByPositionAsc(Long productId);

    Optional<ProductAttribute> findByProductIdAndAttributeId(Long productId, Long attributeId);
}
