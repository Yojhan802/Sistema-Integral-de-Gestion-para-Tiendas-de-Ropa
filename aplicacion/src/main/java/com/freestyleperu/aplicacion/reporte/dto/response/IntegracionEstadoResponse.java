package com.freestyleperu.aplicacion.reporte.dto.response;

import java.math.BigDecimal;

public record IntegracionEstadoResponse(
        String provider,
        String status,
        long count,
        BigDecimal total) {
}
