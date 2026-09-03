package com.freestyleperu.aplicacion.plataforma.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Una línea del historial de paquete, tal como se muestra en el panel. */
public record CambioPaqueteResponse(
        LocalDateTime fecha,
        String usuario,
        BigDecimal totalAnterior,
        BigDecimal totalNuevo,
        List<String> agregados,
        List<String> quitados) {
}
