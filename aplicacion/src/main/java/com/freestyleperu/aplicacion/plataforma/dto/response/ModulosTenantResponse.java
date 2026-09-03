package com.freestyleperu.aplicacion.plataforma.dto.response;

import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Estado completo de los módulos de una empresa. Incluye los presets para que el panel
 * pueda aplicar un plan sin otra llamada, y el total del paquete, que es el dato con el
 * que se negocia frente al presupuesto del cliente.
 */
public record ModulosTenantResponse(
        Long tenantId,
        String empresa,
        Plan plan,
        List<ModuloResponse> modulos,
        BigDecimal totalMensual,
        Map<Plan, List<ModuloSistema>> presets) {
}
