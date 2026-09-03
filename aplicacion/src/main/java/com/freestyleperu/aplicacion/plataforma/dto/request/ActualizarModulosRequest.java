package com.freestyleperu.aplicacion.plataforma.dto.request;

import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Módulos que el operador quiere dejar contratados. No hace falta enviar las
 * dependencias: el servidor cierra el conjunto, porque confiar en que el cliente las
 * calcule bien es justo lo que deja empresas en un estado imposible.
 */
public record ActualizarModulosRequest(@NotNull @Valid List<ModuloSeleccionado> modulos) {

    public record ModuloSeleccionado(
            @NotNull ModuloSistema code,
            @NotNull @DecimalMin(value = "0.00", message = "no puede ser negativo") BigDecimal precioMensual) {
    }
}
