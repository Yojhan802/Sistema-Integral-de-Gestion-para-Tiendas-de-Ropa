package com.freestyleperu.aplicacion.tienda.dto.response;

/** Opciones públicas de comprobante, sin exponer credenciales ni configuración sensible. */
public record PublicBillingOptionsResponse(
        boolean electronicInvoicingEnabled,
        boolean available,
        boolean receiptAvailable,
        boolean invoiceAvailable,
        String provider) {
}
