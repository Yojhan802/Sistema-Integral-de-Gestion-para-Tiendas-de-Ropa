package com.freestyleperu.aplicacion.reserva.domain;

import com.freestyleperu.aplicacion.combo.domain.Combo;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;
import lombok.Setter;

/**
 * Un producto apartado dentro de una separación. Cuando viene de aplicar un
 * combo (botón "+ Agregar combo" en el panel), {@code combo} queda fijado
 * desde la creación junto con su descuento ya repartido — no se vuelve a
 * calcular al completar el pago. {@code comboGroup} distingue dos
 * aplicaciones del mismo combo dentro de una misma separación (ej. "8 polos"
 * = 2 aplicaciones de un combo de 4), para que el backend no las mezcle en
 * un solo grupo que no calzaría con la definición del combo.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reservation_details")
public class ReservaDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ver Javadoc de {@code BaseEntity.tenantId} — esta entidad no extiende BaseEntity pero también se aísla por tenant. */
    @TenantId
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reserva reserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    @Column(name = "combo_group")
    private Integer comboGroup;
}
