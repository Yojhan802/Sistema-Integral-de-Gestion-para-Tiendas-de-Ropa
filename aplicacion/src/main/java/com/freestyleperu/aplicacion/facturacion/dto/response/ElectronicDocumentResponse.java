package com.freestyleperu.aplicacion.facturacion.dto.response;

import com.freestyleperu.aplicacion.facturacion.domain.BillingProvider;
import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentStatus;
import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ElectronicDocumentResponse(
        Long id,
        Long saleId,
        String saleNumber,
        Long sourceDocumentId,
        BillingProvider provider,
        ElectronicDocumentType documentType,
        ElectronicDocumentStatus status,
        String series,
        String documentNumber,
        BigDecimal amount,
        String currencyCode,
        String providerDocumentId,
        String providerStatus,
        String reasonCode,
        String reasonDescription,
        String cdrCode,
        String cdrMessage,
        String pdfUrl,
        String xmlUrl,
        String cdrUrl,
        LocalDateTime submittedAt,
        LocalDateTime acceptedAt,
        LocalDateTime rejectedAt,
        LocalDateTime createdAt) {
}
