package com.freestyleperu.aplicacion.facturacion.port;

import com.freestyleperu.aplicacion.facturacion.domain.BillingProvider;

public interface ElectronicInvoicingProvider {

    BillingProvider type();

    ElectronicInvoicingResult issue(
            ElectronicInvoicingCommand command, BillingConfigurationData configuration);

    ElectronicInvoicingResult fetchStatus(String providerDocumentId, BillingConfigurationData configuration);

    ElectronicInvoicingResult retry(String providerDocumentId, BillingConfigurationData configuration);

    /** Permite a proveedores que necesitan el comprobante completo para reintentar el envío. */
    default ElectronicInvoicingResult retry(
            ElectronicInvoicingCommand command, String providerDocumentId,
            BillingConfigurationData configuration) {
        return retry(providerDocumentId, configuration);
    }

    ElectronicInvoicingResource download(
            String providerDocumentId, String resource, BillingConfigurationData configuration);
}
