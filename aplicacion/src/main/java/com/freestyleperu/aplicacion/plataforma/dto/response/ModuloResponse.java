package com.freestyleperu.aplicacion.plataforma.dto.response;

import com.freestyleperu.aplicacion.plataforma.domain.ModuloSistema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Un módulo tal como lo ve el panel del operador. Además de si está contratado, viaja
 * por qué no se puede desmarcar: el panel bloquea la casilla en vez de dejar guardar
 * una combinación que no puede funcionar.
 */
public record ModuloResponse(
        ModuloSistema code,
        String nombre,
        String descripcion,
        ModuloSistema.Tipo tipo,
        boolean contratado,
        /** Se activó solo porque otro módulo contratado lo necesita. */
        boolean incluidoPorDependencia,
        boolean bloqueado,
        String motivoBloqueo,
        BigDecimal precioMensual,
        BigDecimal precioLista,
        List<ModuloSistema> requiere) {
}
