package com.freestyleperu.aplicacion.plataforma.repository;

import com.freestyleperu.aplicacion.plataforma.domain.TenantModuleChange;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleChangeRepository extends JpaRepository<TenantModuleChange, Long> {

    /** El id desempata: dos cambios del mismo instante deben salir en orden de registro. */
    List<TenantModuleChange> findAllByTenantIdOrderByChangedAtDescIdDesc(Long tenantId, Pageable pageable);
}
