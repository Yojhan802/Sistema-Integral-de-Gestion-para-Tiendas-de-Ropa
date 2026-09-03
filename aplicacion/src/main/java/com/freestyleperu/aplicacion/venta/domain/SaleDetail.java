package com.freestyleperu.aplicacion.venta.domain;

import com.freestyleperu.aplicacion.combo.domain.Combo;
import com.freestyleperu.aplicacion.producto.domain.ProductVariant;
import com.freestyleperu.aplicacion.promocion.domain.Promocion;
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
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Guarda una foto del nombre/SKU/precio en el momento de la venta (decisión
 * D-05): si el producto cambia después, el histórico de ventas no se altera.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sale_details")
public class SaleDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ver Javadoc de {@code BaseEntity.tenantId} — esta entidad no extiende BaseEntity pero también se aísla por tenant. */
    @TenantId
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    /** No nulo cuando esta línea viene de vender un combo (docs/03 §18) — solo trazabilidad, no cambia el cálculo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    /** No nulo cuando el descuento de esta línea vino de aplicar una promoción, no de un descuento manual. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promocion promotion;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "variant_sku", nullable = false, length = 60)
    private String variantSku;

    @Column(name = "variant_label", nullable = false, length = 150)
    private String variantLabel;
}
