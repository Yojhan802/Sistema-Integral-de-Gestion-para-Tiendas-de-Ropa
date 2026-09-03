package com.freestyleperu.aplicacion.tienda.repository;

import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.tienda.domain.StorefrontBanner;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorefrontBannerRepository extends JpaRepository<StorefrontBanner, Long> {

    List<StorefrontBanner> findAllByStatusOrderBySortOrderAscIdAsc(EstadoGeneral status);
}
