package com.freestyleperu.aplicacion.plataforma.dto.response;

import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import java.util.List;
import java.util.Map;

/**
 * Catálogo de módulos sin empresa detrás: lo consume el alta, donde todavía no existe
 * un tenant al que consultarle su paquete.
 */
public record CatalogoModulosResponse(
        List<ModuloResponse> modulos,
        Map<Plan, List<ModuloSistema>> presets) {
}
