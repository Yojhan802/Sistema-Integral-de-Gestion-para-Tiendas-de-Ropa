package com.freestyleperu.aplicacion.pago.domain;

import com.freestyleperu.aplicacion.pedido.domain.Pedido;
import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import com.freestyleperu.aplicacion.venta.domain.Sale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Intento de cobro de una operación online. No guarda números de tarjeta, CVV,
 * tokens del navegador ni credenciales del proveedor.
 *
 * <p>Un intento pertenece a un pedido o a una venta, pero nunca a ambos. La
 * venta se enlazará cuando el pago aprobado se materialice en el flujo
 * correspondiente.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_transactions", uniqueConstraints = @UniqueConstraint(
        name = "uk_payment_transaction_tenant_idempotency",
        columnNames = { "tenant_id", "idempotency_key" }))
public class PaymentTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Pedido order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private PaymentProviderType provider;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "PEN";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentTransactionStatus status = PaymentTransactionStatus.CREATED;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "provider_transaction_id", length = 150)
    private String providerTransactionId;

    @Column(name = "provider_reference", length = 255)
    private String providerReference;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Lob
    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
