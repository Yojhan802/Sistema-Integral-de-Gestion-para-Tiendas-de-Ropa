package com.freestyleperu.aplicacion.facturacion.port;

public record ElectronicInvoicingResource(byte[] content, String contentType, String fileName) {
}
