package com.freestyleperu.aplicacion.shared.util;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Correlativos con nombre (SKU, código interno, código de barras, número de venta/pedido...).
 * Se lee con bloqueo pesimista para que dos operaciones concurrentes nunca
 * obtengan el mismo número (ver docs/03-modelo-datos.md §11).
 *
 * <p>La clave primaria es un {@code id} autoincremental, no {@code name}: bajo multi-tenant,
 * dos negocios distintos necesitan poder tener ambos una secuencia llamada "VENTA" — lo único
 * globalmente único es la combinación {@code (tenantId, name)} (ver migración V54).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sequences")
public class Sequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ver Javadoc de {@code BaseEntity.tenantId} — esta entidad no extiende BaseEntity pero también se aísla por tenant. */
    @TenantId
    private Long tenantId;

    @Column(name = "name", length = 40, nullable = false)
    private String name;

    @Column(name = "prefix", length = 10)
    private String prefix;

    @Column(name = "current_value", nullable = false)
    private long currentValue;

    @Column(name = "padding", nullable = false)
    private int padding;
}
