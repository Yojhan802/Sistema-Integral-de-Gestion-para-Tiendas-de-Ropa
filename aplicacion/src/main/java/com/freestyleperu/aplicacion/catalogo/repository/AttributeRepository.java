package com.freestyleperu.aplicacion.catalogo.repository;

import com.freestyleperu.aplicacion.catalogo.domain.Attribute;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeRepository extends JpaRepository<Attribute, Long> {

    boolean existsByNameIgnoreCase(String name);

    List<Attribute> findAllByOrderByNameAsc();
}
