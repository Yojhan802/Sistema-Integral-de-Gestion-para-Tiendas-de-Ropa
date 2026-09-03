package com.freestyleperu.aplicacion.facturacion.domain;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "electronic_documents")
public class ElectronicDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id")
    private ElectronicDocument sourceDocument;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private BillingProvider provider = BillingProvider.VERIFACT;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private ElectronicDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ElectronicDocumentStatus status = ElectronicDocumentStatus.DRAFT;

    @Column(name = "series", nullable = false, length = 10)
    private String series;

    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Snapshot inmutable de la venta y del cliente usado para emitir/reintentar. */
    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "provider_document_id", length = 150)
    private String providerDocumentId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "cdr_code", length = 100)
    private String cdrCode;

    @Lob
    @Column(name = "cdr_message", columnDefinition = "TEXT")
    private String cdrMessage;

    @Column(name = "pdf_url", length = 1000)
    private String pdfUrl;

    @Column(name = "xml_url", length = 1000)
    private String xmlUrl;

    @Column(name = "cdr_url", length = 1000)
    private String cdrUrl;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;
}
