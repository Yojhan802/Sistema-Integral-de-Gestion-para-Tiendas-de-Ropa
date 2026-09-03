package com.freestyleperu.aplicacion.pedido.domain;

import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.venta.domain.Sale;
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

/**
 * Pedido de la tienda online. Al crearse todavía no es una venta (el pago es
 * manual, así que "pedido creado" no significa "pago recibido"). Al
 * confirmar el pago ({@code PedidoService.confirmarPago}) sí se genera una
 * {@link Sale} real (sin sesión de caja, porque el pago online nunca pasa
 * por caja física) — ver docs/03-modelo-datos.md.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "order_number" }))
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ver Javadoc de {@code BaseEntity.tenantId} — esta entidad no extiende BaseEntity pero también se aísla por tenant. */
    @TenantId
    private Long tenantId;

    @Column(name = "order_number", nullable = false, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "department", nullable = false, length = 100)
    private String department;

    @Column(name = "province", nullable = false, length = 100)
    private String province;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PedidoStatus status = PedidoStatus.PENDING_PAYMENT;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference", length = 50)
    private String paymentReference;

    @Column(name = "payment_proof_url", length = 255)
    private String paymentProofUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_document_type", nullable = false, length = 20)
    private PedidoBillingDocumentType billingDocumentType = PedidoBillingDocumentType.TICKET;

    @Column(name = "billing_document_number", length = 15)
    private String billingDocumentNumber;

    @Column(name = "billing_name", length = 150)
    private String billingName;

    /** Cuándo el comprador aceptó los Términos y Condiciones al confirmar el pedido. */
    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    /** Versión del texto aceptado, para poder probar qué condiciones regían esa compra. */
    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "recipient_dni", nullable = false, length = 15)
    private String recipientDni;

    @Column(name = "recipient_first_name", nullable = false, length = 100)
    private String recipientFirstName;

    @Column(name = "recipient_last_name_paterno", nullable = false, length = 60)
    private String recipientLastNamePaterno;

    @Column(name = "recipient_last_name_materno", nullable = false, length = 60)
    private String recipientLastNameMaterno;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private Usuario confirmedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    /** No nulo desde que se confirma el pago — la venta real que generó este pedido. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;
}
