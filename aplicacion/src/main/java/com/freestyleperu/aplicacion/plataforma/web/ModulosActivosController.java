package com.freestyleperu.aplicacion.plataforma.web;

import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import com.freestyleperu.aplicacion.plataforma.service.ModuloGate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Módulos que tiene contratados la empresa de la sesión actual. Lo consume el panel para
 * no mostrar entradas de menú que el servidor va a rechazar igualmente.
 *
 * <p>Es solo cosmética: el acceso lo decide {@link ModuloGate} en cada endpoint. Por eso
 * basta con estar autenticado para leerlo, y no expone nada más que los códigos.
 */
@RestController
public class ModulosActivosController {

    private final ModuloGate moduloGate;

    public ModulosActivosController(ModuloGate moduloGate) {
        this.moduloGate = moduloGate;
    }

    @GetMapping("/api/system/modules")
    public List<ModuloSistema> modulosActivos() {
        return List.copyOf(moduloGate.modulosDelTenantActual());
    }
}
