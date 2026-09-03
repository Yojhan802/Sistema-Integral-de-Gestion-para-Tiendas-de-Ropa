package com.freestyleperu.aplicacion.catalogo.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un valor concreto de un {@link Attribute} ("Rojo", "M", "220V"). {@code hexCode} solo tiene
 * sentido cuando {@code attribute.inputType == SWATCH}. {@code sortOrder} generaliza el orden
 * de tallas (XS&lt;S&lt;M&lt;L) a cualquier atributo — para Color no aporta, se deja en 0.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attribute_values", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "attribute_id", "`value`" }))
public class AttributeValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private Attribute attribute;

    // Nombre entre backticks a propósito: "VALUE" es palabra reservada en H2 (usado en los tests
    // con create-drop) aunque no en MySQL — esto fuerza a Hibernate a citar el identificador
    // siempre, evitando que la creación de la tabla falle en silencio contra H2 (se detectó
    // porque INSERT fallaba con "Table ATTRIBUTE_VALUES not found": la tabla nunca se creó).
    @Column(name = "`value`", nullable = false, length = 40)
    private String value;

    @Column(name = "hex_code", length = 7)
    private String hexCode;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoGeneral status = EstadoGeneral.ACTIVE;
}
