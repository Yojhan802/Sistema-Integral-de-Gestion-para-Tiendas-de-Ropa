package com.freestyleperu.aplicacion.facturacion.port;

import com.freestyleperu.aplicacion.facturacion.domain.BillingProviderEnvironment;
import java.util.Map;

public record BillingConfigurationData(
        BillingProviderEnvironment environment,
        String apiUrl,
        Map<String, String> credentials) {
}
