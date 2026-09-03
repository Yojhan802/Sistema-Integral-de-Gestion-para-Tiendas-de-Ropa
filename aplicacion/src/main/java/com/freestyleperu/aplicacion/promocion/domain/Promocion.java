package com.freestyleperu.aplicacion.promocion.domain;

import com.freestyleperu.aplicacion.catalogo.domain.Category;
import com.freestyleperu.aplicacion.producto.domain.Product;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Promoción con descuento en % o monto fijo, alcance opcional (todo / una
 * categoría / un producto) y vigencia opcional por fechas. Nunca se aplica
 * sola: el cajero la elige por línea de venta (RN-28) — así una promo
 * "solo para el live" puede existir sin colarse en una venta de tienda
 * física, sin que el sistema necesite un concepto de canal de venta.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promotions", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "code" }))
public class Promocion extends BaseEntity {

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private PromotionType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 20)
    private PromotionScope scopeType = PromotionScope.ALL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_category_id")
    private Category scopeCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_product_id")
    private Product scopeProduct;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoGeneral status = EstadoGeneral.ACTIVE;

    /** Si además se refleja sola en la tienda online (precio ya rebajado), sin que un cajero la elija — ver RN-28. */
    @Column(name = "visible_online", nullable = false)
    private boolean visibleOnline = false;
}
