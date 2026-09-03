package com.freestyleperu.aplicacion.venta.domain;

import com.freestyleperu.aplicacion.caja.domain.CashSession;
import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.promotor.domain.Promoter;
import com.freestyleperu.aplicacion.pedido.domain.PedidoBillingDocumentType;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sales", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "sale_number" }))
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ver Javadoc de {@code BaseEntity.tenantId} — esta entidad no extiende BaseEntity pero también se aísla por tenant. */
    @TenantId
    private Long tenantId;

    @Column(name = "sale_number", nullable = false, length = 20)
    private String saleNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Usuario user;

    /** Quién ofreció la prenda en piso, si alguien lo hizo (opcional, solo para comisión/reportes). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id")
    private Promoter promoter;

    /** Nulo cuando la venta viene de confirmar un pedido online — esos nunca pasan por una sesión de caja física. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_session_id")
    private CashSession cashSession;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Costo de envío cuando la venta viene de un pedido online (0 para ventas de POS/separaciones). */
    @Column(name = "shipping_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    /** Datos de facturación congelados al materializar un pedido online. */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_document_type", nullable = false, length = 20)
    private PedidoBillingDocumentType billingDocumentType = PedidoBillingDocumentType.TICKET;

    @Column(name = "billing_document_number", length = 15)
    private String billingDocumentNumber;

    @Column(name = "billing_name", length = 150)
    private String billingName;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private Usuario cancelledBy;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;
}
