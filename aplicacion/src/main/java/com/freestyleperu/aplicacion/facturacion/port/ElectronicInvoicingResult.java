package com.freestyleperu.aplicacion.facturacion.port;

import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentStatus;

public record ElectronicInvoicingResult(
        ElectronicDocumentStatus status,
        String providerDocumentId,
        String providerStatus,
        String providerSeries,
        String providerNumber,
        String cdrCode,
        String cdrMessage,
        String xmlUrl,
        String cdrUrl) {
}
