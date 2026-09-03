package com.freestyleperu.aplicacion.usuario.repository;

import com.freestyleperu.aplicacion.usuario.domain.Permiso;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    List<Permiso> findAllByOrderByModuleAscCodeAsc();

    java.util.Optional<Permiso> findByCode(String code);
}
