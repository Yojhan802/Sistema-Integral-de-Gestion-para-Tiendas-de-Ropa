package com.freestyleperu.aplicacion.facturacion.dto.response;

import com.freestyleperu.aplicacion.facturacion.domain.BillingProvider;
import com.freestyleperu.aplicacion.facturacion.domain.BillingProviderEnvironment;
import java.util.List;

public record BillingConfigurationResponse(
        BillingProvider provider,
        boolean enabled,
        BillingProviderEnvironment environment,
        String apiUrl,
        String invoiceSeries,
        String receiptSeries,
        String creditNoteSeries,
        String debitNoteSeries,
        boolean configured,
        List<String> credentialKeys) {
}
