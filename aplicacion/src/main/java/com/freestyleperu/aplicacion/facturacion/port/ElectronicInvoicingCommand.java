package com.freestyleperu.aplicacion.facturacion.port;

import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentType;

public record ElectronicInvoicingCommand(
        ElectronicDocumentType documentType,
        String series,
        String documentNumber,
        String payloadJson) {
}
