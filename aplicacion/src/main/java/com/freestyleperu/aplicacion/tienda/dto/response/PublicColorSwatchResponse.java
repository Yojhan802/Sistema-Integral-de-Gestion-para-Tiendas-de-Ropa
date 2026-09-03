package com.freestyleperu.aplicacion.tienda.dto.response;

/**
 * Un swatch para la tarjeta de producto del listado — ya no exclusivo del atributo "Color":
 * cualquier atributo que el tenant configure como {@code AttributeInputType.SWATCH} aparece acá
 * (ver {@code TiendaCatalogoService.toResumen}).
 */
public record PublicColorSwatchResponse(String name, String hexCode) {
}
