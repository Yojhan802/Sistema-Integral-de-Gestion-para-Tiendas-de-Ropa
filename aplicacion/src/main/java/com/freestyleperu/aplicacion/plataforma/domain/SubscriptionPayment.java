package com.freestyleperu.aplicacion.plataforma.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un pago de mensualidad. Antes renovar solo corría {@code nextPaymentDue}, que se pisa
 * en cada renovación: no quedaba cuánto se pagó, cómo ni qué periodo cubría.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment {

    /** Cómo entró el cobro. ONLINE queda reservado para el pago por pasarela. */
    public enum Origen { MANUAL, ONLINE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "method", nullable = false, length = 30)
    private String method;

    @Column(name = "reference", length = 80)
    private String reference;

    /** Captura del Yape o de la transferencia, adjunta al registrar el pago. */
    @Column(name = "proof_url", length = 255)
    private String proofUrl;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private Origen source = Origen.MANUAL;

    @Column(name = "registered_by")
    private Long registeredBy;

    @Column(name = "registered_by_username", length = 50)
    private String registeredByUsername;

    @Column(name = "notes", length = 255)
    private String notes;
}
