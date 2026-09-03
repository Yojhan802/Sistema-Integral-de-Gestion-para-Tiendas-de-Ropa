package com.freestyleperu.aplicacion.pedido.domain;

/**
 * Documento solicitado por el comprador en la tienda online.
 *
 * TICKET es el comprobante interno de la operación cuando el negocio no tiene
 * facturación electrónica habilitada. No se envía a SUNAT ni sustituye una
 * boleta o factura electrónica.
 */
public enum PedidoBillingDocumentType {
    TICKET,
    BOLETA,
    FACTURA
}
