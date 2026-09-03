package com.freestyleperu.aplicacion.catalogo.repository;

import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeValueRepository extends JpaRepository<AttributeValue, Long> {

    boolean existsByAttributeIdAndValueIgnoreCase(Long attributeId, String value);

    List<AttributeValue> findAllByAttributeIdOrderBySortOrderAscValueAsc(Long attributeId);
}
