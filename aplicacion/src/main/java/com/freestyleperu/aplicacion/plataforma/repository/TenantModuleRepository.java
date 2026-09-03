package com.freestyleperu.aplicacion.plataforma.repository;

import com.freestyleperu.aplicacion.plataforma.domain.TenantModule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleRepository extends JpaRepository<TenantModule, Long> {

    List<TenantModule> findAllByTenantId(Long tenantId);

    void deleteAllByTenantId(Long tenantId);
}
