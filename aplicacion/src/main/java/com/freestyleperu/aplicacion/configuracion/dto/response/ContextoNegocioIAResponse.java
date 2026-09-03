package com.freestyleperu.aplicacion.configuracion.dto.response;

import com.freestyleperu.aplicacion.configuracion.domain.BusinessVertical;

/**
 * Lo que necesitan los prompts de IA (AsistenteTiendaService, ProductoAsistenteService,
 * ReporteAsistenteService) para dejar de hardcodear "negocio de ropa" — {@code vertical} decide
 * qué instrucciones se activan (armador de outfits, guardrail talla-color), {@code frase} es el
 * framing textual hacia el cliente/usuario (ya resuelto: {@code businessDescription} del tenant
 * si lo configuró, o un texto genérico según {@code vertical} si no).
 */
public record ContextoNegocioIAResponse(BusinessVertical vertical, String frase) {
}
