package com.freestyleperu.aplicacion.pago.provider;

import com.freestyleperu.aplicacion.pago.domain.PaymentProvider;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransactionStatus;
import com.freestyleperu.aplicacion.pago.exception.ProveedorPagoException;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeCommand;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeResult;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptador para cargos únicos de Culqi API v2. */
@Component
public class CulqiProvider implements PaymentProvider {

    private static final String DEFAULT_API_URL = "https://api.culqi.com/v2";

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.CULQI;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentProviderChargeResult charge(
            PaymentProviderChargeCommand command, PaymentProviderConfigurationData configuration) {
        String secretKey = credential(configuration, "secretKey", "privateKey", "apiKey");
        if (secretKey == null) {
            throw new ProveedorPagoException("Falta la llave privada de Culqi en la configuración de la empresa");
        }
        if (command.customerEmail() == null || command.customerEmail().isBlank()) {
            throw new ProveedorPagoException("El cliente debe tener un correo para pagar con Culqi");
        }

        int amountInCents;
        try {
            amountInCents = command.amount().setScale(2).movePointRight(2).intValueExact();
        } catch (ArithmeticException ex) {
            throw new ProveedorPagoException("El monto de la transacción no es válido para Culqi");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountInCents);
        body.put("currency_code", command.currencyCode());
        body.put("email", command.customerEmail());
        body.put("source_id", command.sourceId());
        body.put("capture", true);
        body.put("description", "Pedido " + command.orderNumber());
        body.put("metadata", Map.of(
                "order_number", command.orderNumber(),
                "payment_transaction_id", String.valueOf(command.transactionId())));

        try {
            ResponseEntity<Map> response = RestClient.builder()
                    .baseUrl(apiUrl(configuration))
                    .build()
                    .post()
                    .uri("/charges")
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);

            Map<String, Object> responseBody = response.getBody();
            String providerId = text(responseBody, "id");
            if (providerId == null) {
                return new PaymentProviderChargeResult(
                        PaymentTransactionStatus.FAILED, null, null, "MISSING_PROVIDER_ID",
                        "Culqi no devolvió un identificador de cargo");
            }
            if (response.getStatusCode().value() == 200) {
                return new PaymentProviderChargeResult(
                        PaymentTransactionStatus.PENDING, providerId, text(responseBody, "reference_code"),
                        "3DS_REQUIRED", "El pago requiere autenticación adicional");
            }
            if (response.getStatusCode().value() != 201) {
                return new PaymentProviderChargeResult(
                        PaymentTransactionStatus.FAILED, providerId, text(responseBody, "reference_code"),
                        "UNEXPECTED_PROVIDER_STATUS", "Culqi devolvió un estado no esperado");
            }
            return new PaymentProviderChargeResult(
                    PaymentTransactionStatus.APPROVED, providerId, text(responseBody, "reference_code"), null, null);
        } catch (RestClientException ex) {
            return new PaymentProviderChargeResult(
                    PaymentTransactionStatus.FAILED, null, null, "PROVIDER_REQUEST_FAILED",
                    "No se pudo comunicar con Culqi en este momento");
        }
    }

    private String apiUrl(PaymentProviderConfigurationData configuration) {
        String configured = configuration.apiUrl();
        String url = configured == null || configured.isBlank() ? DEFAULT_API_URL : configured.trim();
        if (configuration.environment() == PaymentProviderEnvironment.PRODUCTION
                && !url.startsWith("https://")) {
            throw new ProveedorPagoException("La URL de Culqi en producción debe usar HTTPS");
        }
        return url.replaceAll("/+$", "");
    }

    private String credential(PaymentProviderConfigurationData configuration, String... keys) {
        for (String key : keys) {
            String value = configuration.credentials().get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }
}
