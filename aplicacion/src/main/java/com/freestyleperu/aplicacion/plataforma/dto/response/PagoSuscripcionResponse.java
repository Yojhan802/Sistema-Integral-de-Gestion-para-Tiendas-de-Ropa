package com.freestyleperu.aplicacion.plataforma.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una línea del historial de pagos de una empresa. */
public record PagoSuscripcionResponse(
        Long id,
        LocalDateTime fecha,
        BigDecimal monto,
        String metodo,
        String referencia,
        String comprobanteUrl,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        String origen,
        String registradoPor,
        String nota) {
}
