package com.freestyleperu.aplicacion.producto.mapper;

import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.producto.domain.Product;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.producto.domain.VariantAttributeValue;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteAtributoResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteBusquedaResponse;
import com.freestyleperu.aplicacion.producto.dto.response.VarianteResponse;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class VarianteMapper {

    /**
     * @param posicionesPorAtributo {@code attributeId -> ProductAttribute.position} del
     *                               producto de esta variante — define el orden de exhibición
     *                               (ver {@code VarianteService.posicionesDelProducto}).
     */
    public VarianteResponse toResponse(ProductVariant variant, Map<Long, Short> posicionesPorAtributo) {
        var atributos = variant.getAttributeValues().stream()
                .sorted(Comparator.comparing(vav -> posicionesPorAtributo.get(vav.getAttributeValue().getAttribute().getId())))
                .map(this::toAtributoResponse)
                .toList();
        return new VarianteResponse(
                variant.getId(),
                variant.getProduct().getId(),
                variant.getProduct().getName(),
                atributos,
                variant.getVariantLabel(),
                variant.getSku(),
                variant.getBarcode(),
                variant.getStock(),
                variant.getMinStock(),
                variant.getStatus());
    }

    public VarianteBusquedaResponse toBusquedaResponse(ProductVariant variant) {
        Product product = variant.getProduct();
        BigDecimal effectivePrice = product.getPromoPrice() != null ? product.getPromoPrice() : product.getPrice();
        return new VarianteBusquedaResponse(
                variant.getId(),
                product.getName(),
                variant.getVariantLabel(),
                variant.getSku(),
                variant.getBarcode(),
                product.getPrice(),
                product.getPromoPrice(),
                effectivePrice,
                variant.getStock(),
                variant.getStatus());
    }

    private VarianteAtributoResponse toAtributoResponse(VariantAttributeValue vav) {
        AttributeValue value = vav.getAttributeValue();
        return new VarianteAtributoResponse(
                value.getAttribute().getId(),
                value.getAttribute().getName(),
                value.getAttribute().getInputType(),
                value.getId(),
                value.getValue(),
                value.getHexCode());
    }
}
