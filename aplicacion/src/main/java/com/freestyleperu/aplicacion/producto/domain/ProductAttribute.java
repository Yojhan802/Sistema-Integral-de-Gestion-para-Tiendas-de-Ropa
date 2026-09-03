package com.freestyleperu.aplicacion.producto.domain;

import com.freestyleperu.aplicacion.catalogo.domain.Attribute;
import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Qué {@link Attribute} usa un producto y en qué {@code position} — reemplaza "color siempre
 * primero, talla siempre segundo". Define el orden de las columnas de la matriz de variantes
 * en el admin y de los segmentos del SKU autogenerado (ver {@code VarianteService}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_attributes")
public class ProductAttribute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private Attribute attribute;

    @Column(name = "position", nullable = false)
    private short position;
}
