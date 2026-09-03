package com.freestyleperu.aplicacion.pago.domain;

/** Contrato para una pasarela de pago concreta; la configuración vive por tenant. */
public interface PaymentProvider {

    PaymentProviderType type();

    com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeResult charge(
            com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeCommand command,
            com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData configuration);

    /**
     * Inicializa un checkout desacoplado. Los proveedores que tokenizan
     * directamente desde el navegador pueden usar únicamente {@link #charge}.
     */
    default com.freestyleperu.aplicacion.pago.port.PaymentProviderCheckoutResult initializeCheckout(
            com.freestyleperu.aplicacion.pago.port.PaymentProviderCheckoutCommand command,
            com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData configuration) {
        throw new UnsupportedOperationException("El proveedor no ofrece checkout desacoplado");
    }
}
