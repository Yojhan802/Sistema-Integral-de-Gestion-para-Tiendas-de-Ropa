package com.freestyleperu.aplicacion.reclamo.domain;

import com.freestyleperu.aplicacion.shared.domain.BaseEntity;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "complaint_book_entries", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "entry_number" }))
public class ComplaintBookEntry extends BaseEntity {

    @Column(name = "entry_number", nullable = false, length = 30)
    private String entryNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private ComplaintType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ComplaintStatus status = ComplaintStatus.PENDIENTE;

    @Column(name = "provider_name", nullable = false, length = 150)
    private String providerName;

    @Column(name = "provider_ruc", length = 11)
    private String providerRuc;

    @Column(name = "provider_address", length = 255)
    private String providerAddress;

    @Column(name = "consumer_name", nullable = false, length = 150)
    private String consumerName;

    @Column(name = "consumer_document", length = 20)
    private String consumerDocument;

    @Column(name = "consumer_email", nullable = false, length = 150)
    private String consumerEmail;

    @Column(name = "consumer_phone", length = 20)
    private String consumerPhone;

    /** Domicilio del consumidor — exigido por el Anexo II del D.S. 011-2011-PCM. */
    @Column(name = "consumer_address", length = 255)
    private String consumerAddress;

    @Column(name = "order_number", length = 30)
    private String orderNumber;

    @Column(name = "sale_number", length = 30)
    private String saleNumber;

    @Column(name = "product_service_description", nullable = false, length = 255)
    private String productServiceDescription;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "detail", nullable = false, columnDefinition = "TEXT")
    private String detail;

    @Column(name = "consumer_request", nullable = false, columnDefinition = "TEXT")
    private String consumerRequest;

    @Column(name = "response", columnDefinition = "TEXT")
    private String response;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by")
    private Usuario respondedBy;
}
