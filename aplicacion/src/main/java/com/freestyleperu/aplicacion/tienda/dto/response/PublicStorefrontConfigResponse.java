package com.freestyleperu.aplicacion.tienda.dto.response;

import com.freestyleperu.aplicacion.configuracion.domain.StoreTemplate;
import java.math.BigDecimal;

/**
 * Configuracion publica para renderizar el storefront.
 *
 * <p>Ademas de la apariencia incluye la identificacion del proveedor (razon
 * social, RUC y domicilio fiscal) y el contacto: el Codigo de Proteccion y
 * Defensa del Consumidor obliga a mostrarlos al comprador, asi que son datos
 * publicos por diseno, no una fuga. {@code igvRate} permite declarar en la
 * tienda que los precios ya incluyen impuestos.
 */
public record PublicStorefrontConfigResponse(
        StoreTemplate template,
        String primaryColor,
        String accentColor,
        String backgroundColor,
        String legalName,
        String ruc,
        String address,
        String phone,
        String email,
        BigDecimal igvRate,
        String currencyCode,
        String currencySymbol) {
}
