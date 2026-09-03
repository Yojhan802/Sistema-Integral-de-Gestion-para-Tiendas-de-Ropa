package com.freestyleperu.aplicacion.plataforma.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Pago de mensualidad que registra el operador.
 *
 * <p>{@code meses} permite cubrir varios periodos con un solo cobro, que es lo que pasa
 * cuando una empresa se pone al día tras varios meses de atraso.
 */
public record RegistrarPagoRequest(
        @NotNull @DecimalMin(value = "0.00", message = "no puede ser negativo") BigDecimal monto,
        @NotBlank @Size(max = 30) String metodo,
        @Size(max = 80) String referencia,
        @Min(1) @Max(24) Integer meses,
        @Size(max = 255) String nota) {

    public int mesesODefecto() {
        return meses == null ? 1 : meses;
    }
}
