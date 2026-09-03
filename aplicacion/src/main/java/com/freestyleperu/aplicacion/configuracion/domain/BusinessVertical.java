package com.freestyleperu.aplicacion.configuracion.domain;

/**
 * Rubro del negocio — decide si el asistente de IA activa las instrucciones específicas de ropa
 * (armador de outfits, guardrail de combinaciones talla-color) o unas genéricas armadas sobre
 * los {@code Attribute} reales configurados por el tenant (ver AsistenteTiendaService).
 */
public enum BusinessVertical {
    CLOTHING,
    GENERAL
}
