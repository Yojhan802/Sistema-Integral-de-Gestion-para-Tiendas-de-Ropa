package com.freestyleperu.aplicacion.catalogo.repository;

import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    List<Category> findAllByOrderByNameAsc();

    List<Category> findTop5ByStatusAndNameContainingIgnoreCaseOrderByNameAsc(EstadoGeneral status, String name);
}
