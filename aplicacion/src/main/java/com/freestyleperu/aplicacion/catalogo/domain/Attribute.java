package com.freestyleperu.aplicacion.catalogo.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tipo de atributo configurable por tenant ("Color", "Talla", "Voltaje"...) — reemplaza las
 * entidades fijas {@code Color}/{@code Size}. Cada producto elige qué atributos usa vía
 * {@link com.freestyleperu.aplicacion.producto.domain.ProductAttribute}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "attributes", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "name" }))
public class Attribute extends BaseEntity {

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_type", nullable = false, length = 20)
    private AttributeInputType inputType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoGeneral status = EstadoGeneral.ACTIVE;
}
