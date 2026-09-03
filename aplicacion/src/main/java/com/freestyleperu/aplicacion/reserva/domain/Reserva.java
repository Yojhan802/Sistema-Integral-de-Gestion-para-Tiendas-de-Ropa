package com.freestyleperu.aplicacion.reserva.domain;

import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.pago.domain.PaymentMethod;
import com.freestyleperu.aplicacion.promotor.domain.Promoter;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.venta.domain.Sale;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Separación (layaway): el cliente paga una seña para apartar uno o varios
 * productos ({@link ReservaDetail}) — a diferencia de un pedido online, el
 * stock se retira de inmediato al crearla (ver ReservaService, docs/03
 * §17). Plan PROFESIONAL.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reservations", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "reservation_number" }))
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ver Javadoc de {@code BaseEntity.tenantId} — esta entidad no extiende BaseEntity pero también se aísla por tenant. */
    @TenantId
    private Long tenantId;

    @Column(name = "reservation_number", nullable = false, length = 20)
    private String reservationNumber;

    /** Nulo cuando la separación es de un comprador ocasional no registrado — ver {@code guestName}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Nombre del comprador ocasional (lives de TikTok, etc.) cuando no hay {@code customer}. */
    @Column(name = "guest_name", length = 150)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservaDetail> details = new ArrayList<>();

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposit_payment_method_id", nullable = false)
    private PaymentMethod depositPaymentMethod;

    @Column(name = "deposit_reference", length = 50)
    private String depositReference;

    /** Quién generó la venta (ej. el live de TikTok), si alguien lo hizo — solo para comisión/reportes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id")
    private Promoter promoter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservaStatus status = ReservaStatus.RESERVADO;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "notes", length = 255)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private Usuario completedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private Usuario cancelledBy;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;
}
