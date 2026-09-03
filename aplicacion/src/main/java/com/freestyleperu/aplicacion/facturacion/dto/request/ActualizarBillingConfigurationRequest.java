package com.freestyleperu.aplicacion.facturacion.dto.request;

import com.freestyleperu.aplicacion.facturacion.domain.BillingProviderEnvironment;
import com.freestyleperu.aplicacion.facturacion.domain.BillingProvider;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ActualizarBillingConfigurationRequest(
        BillingProvider provider,
        @NotNull Boolean enabled,
        @NotNull BillingProviderEnvironment environment,
        @Size(max = 500) String apiUrl,
        @Size(max = 10) String invoiceSeries,
        @Size(max = 10) String receiptSeries,
        @Size(max = 10) String creditNoteSeries,
        @Size(max = 10) String debitNoteSeries,
        Map<String, String> credentials) {
}
