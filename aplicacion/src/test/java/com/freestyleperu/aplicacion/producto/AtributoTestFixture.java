package com.freestyleperu.aplicacion.producto;

import com.freestyleperu.aplicacion.catalogo.domain.Attribute;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeInputType;
import com.freestyleperu.aplicacion.catalogo.domain.AttributeValue;
import com.freestyleperu.aplicacion.catalogo.repository.AttributeRepository;
import com.freestyleperu.aplicacion.catalogo.repository.AttributeValueRepository;
import org.springframework.stereotype.Component;

/**
 * Reemplaza a los antiguos {@code ColorRepository}/{@code SizeRepository} en los tests
 * (sistema de atributos genéricos, ver plan aprobado): crea/reutiliza los atributos "Color"
 * (SWATCH) y "Talla" (LIST) del tenant de prueba y sus valores, devolviendo el
 * {@code AttributeValue} listo para pasar su id a {@code CrearVarianteRequest}/
 * {@code GenerarVariantesRequest}.
 */
@Component
public class AtributoTestFixture {

    private final AttributeRepository attributeRepository;
    private final AttributeValueRepository attributeValueRepository;

    public AtributoTestFixture(AttributeRepository attributeRepository, AttributeValueRepository attributeValueRepository) {
        this.attributeRepository = attributeRepository;
        this.attributeValueRepository = attributeValueRepository;
    }

    public AttributeValue color(String nombre) {
        return color(nombre, "#000000");
    }

    public AttributeValue color(String nombre, String hex) {
        return valor(atributo("Color", AttributeInputType.SWATCH), nombre, hex, (short) 0);
    }

    public AttributeValue talla(String nombre, short orden) {
        return valor(atributo("Talla", AttributeInputType.LIST), nombre, null, orden);
    }

    private Attribute atributo(String nombre, AttributeInputType tipo) {
        return attributeRepository.findAllByOrderByNameAsc().stream()
                .filter(a -> a.getName().equalsIgnoreCase(nombre))
                .findFirst()
                .orElseGet(() -> {
                    Attribute attribute = new Attribute();
                    attribute.setName(nombre);
                    attribute.setInputType(tipo);
                    return attributeRepository.save(attribute);
                });
    }

    private AttributeValue valor(Attribute attribute, String valor, String hex, short orden) {
        AttributeValue value = new AttributeValue();
        value.setAttribute(attribute);
        value.setValue(valor);
        value.setHexCode(hex);
        value.setSortOrder(orden);
        return attributeValueRepository.save(value);
    }
}
