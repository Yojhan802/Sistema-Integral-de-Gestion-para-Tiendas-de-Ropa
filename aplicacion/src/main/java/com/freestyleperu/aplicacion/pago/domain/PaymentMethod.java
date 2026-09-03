package com.freestyleperu.aplicacion.pago.domain;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_methods", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "code" }))
public class PaymentMethod extends BaseEntity {

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PaymentMethodType type;

    /** Decide qué cobros entran al arqueo de caja (RN-10): solo efectivo. */
    @Column(name = "affects_cash", nullable = false)
    private boolean affectsCash;

    @Column(name = "requires_reference", nullable = false)
    private boolean requiresReference;

    @Column(name = "account_holder", length = 150)
    private String accountHolder;

    @Column(name = "account_number", length = 30)
    private String accountNumber;

    @Column(name = "qr_image_url", length = 255)
    private String qrImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EstadoGeneral status = EstadoGeneral.ACTIVE;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;
}
