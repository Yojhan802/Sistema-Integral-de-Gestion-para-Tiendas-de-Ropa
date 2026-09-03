package com.freestyleperu.aplicacion.pago.provider;

import com.freestyleperu.aplicacion.pago.domain.PaymentProvider;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransactionStatus;
import com.freestyleperu.aplicacion.pago.exception.ProveedorPagoException;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeCommand;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeResult;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderCheckoutCommand;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderCheckoutResult;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptador para el formulario desacoplado de Punto Web Izipay. */
@Component
public class IzipayProvider implements PaymentProvider {

    private static final String TEST_API_URL = "https://sandbox-api-pw.izipay.pe";
    private static final String PRODUCTION_API_URL = "https://api-pw.izipay.pe";
    private static final String TEST_SCRIPT_URL = "https://sandbox-checkout.izipay.pe/payments/v1/js/index.js";
    private static final String PRODUCTION_SCRIPT_URL = "https://checkout.izipay.pe/payments/v1/js/index.js";
    private static final String SESSION_PATH = "/security/v1/Token/Generate";
    private static final DateTimeFormatter TRANSACTION_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.IZIPAY;
    }

    @Override
    public PaymentProviderCheckoutResult initializeCheckout(
            PaymentProviderCheckoutCommand command, PaymentProviderConfigurationData configuration) {
        String merchantCode = required(configuration.merchantCode(), "el código de comercio de Izipay");
        String publicKey = required(configuration.publicKey(), "la clave pública RSA de Izipay");
        String apiKey = credential(configuration, "newPaymentButtonApiKey", "paymentButtonApiKey", "apiKey", "sessionApiKey");
        if (apiKey == null) {
            throw new ProveedorPagoException("Falta la clave de API del nuevo botón de pagos de Izipay");
        }
        validateAmount(command.amount());

        String correlationId = correlationId(command.transactionId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestSource", "ECOMMERCE");
        body.put("merchantCode", merchantCode);
        body.put("orderNumber", command.orderNumber());
        body.put("publicKey", publicKey);
        body.put("amount", command.amount().setScale(2).toPlainString());
        String ipnUrl = credential(configuration, "ipnUrl", "notificationUrl", "urlIPN");
        if (ipnUrl != null) body.put("urlIPN", ipnUrl.replace("{transactionId}", correlationId));

        try {
            ResponseEntity<Map> response = restClient().post()
                    .uri(endpoint(configuration, "sessionTokenUrl", SESSION_PATH))
                    .headers(headers -> {
                        headers.set("transactionId", correlationId);
                        String prefix = credential(configuration, "apiKeyPrefix");
                        headers.set(apiKeyHeader(configuration), prefix == null ? apiKey : prefix + " " + apiKey);
                    })
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
            Map<String, Object> responseBody = response.getBody();
            String code = text(responseBody, "code");
            Map<String, Object> responseData = map(responseBody, "response");
            String sessionToken = firstText(responseData, "token", "sessionToken", "authorization");
            if (sessionToken == null) sessionToken = firstText(responseBody, "token", "sessionToken", "authorization");
            if (!"00".equals(code) && code != null) {
                throw new ProveedorPagoException("Izipay rechazó la generación del token de sesión: " + code);
            }
            if (sessionToken == null) {
                throw new ProveedorPagoException("Izipay no devolvió el token de sesión del checkout");
            }
            return new PaymentProviderCheckoutResult(
                    PaymentProviderType.IZIPAY,
                    sessionToken,
                    scriptUrl(configuration),
                    merchantCode,
                    correlationId,
                    publicKey,
                    positiveInt(responseBody == null ? null : responseBody.get("expirationTime"), 15));
        } catch (ProveedorPagoException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ProveedorPagoException("No se pudo crear la sesión de pago de Izipay");
        }
    }

    @Override
    public PaymentProviderChargeResult charge(
            PaymentProviderChargeCommand command, PaymentProviderConfigurationData configuration) {
        // En Punto Web el SDK procesa la operación y el IPN confirma el resultado.
        // No se reciben datos de tarjeta ni se intenta autorizar desde este endpoint.
        String sourceId = command.sourceId() == null ? null : command.sourceId().trim();
        if (sourceId == null || sourceId.isBlank()) {
            throw new ProveedorPagoException("Izipay no recibió el identificador de la sesión");
        }
        return new PaymentProviderChargeResult(
                PaymentTransactionStatus.PENDING, sourceId, null, null,
                "El pago de Izipay quedó pendiente de confirmación");
    }

    private String correlationId(Long transactionId) {
        if (transactionId == null || transactionId <= 0) {
            throw new ProveedorPagoException("La transacción interna de Izipay no es válida");
        }
        return String.format("FP%010d", transactionId);
    }

    private RestClient restClient() {
        return RestClient.builder().build();
    }

    private String endpoint(PaymentProviderConfigurationData configuration, String credentialKey, String path) {
        String configured = credential(configuration, credentialKey);
        if (configured != null) return configured;
        return apiUrl(configuration) + path;
    }

    private String apiUrl(PaymentProviderConfigurationData configuration) {
        String configured = configuration.apiUrl();
        String url = configured == null || configured.isBlank()
                ? (configuration.environment() == PaymentProviderEnvironment.PRODUCTION
                        ? PRODUCTION_API_URL : TEST_API_URL)
                : configured.trim();
        if (configuration.environment() == PaymentProviderEnvironment.PRODUCTION
                && !url.startsWith("https://")) {
            throw new ProveedorPagoException("La URL de Izipay en producción debe usar HTTPS");
        }
        return url.replaceAll("/+$", "");
    }

    private String scriptUrl(PaymentProviderConfigurationData configuration) {
        String configured = credential(configuration, "checkoutScriptUrl", "scriptUrl");
        if (configured != null) return configured;
        return configuration.environment() == PaymentProviderEnvironment.PRODUCTION
                ? PRODUCTION_SCRIPT_URL : TEST_SCRIPT_URL;
    }

    private String apiKeyHeader(PaymentProviderConfigurationData configuration) {
        String configured = credential(configuration, "apiKeyHeader");
        return configured == null ? "Authorization" : configured;
    }

    private String credential(PaymentProviderConfigurationData configuration, String... keys) {
        for (String key : keys) {
            String value = configuration.credentials().get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private String required(String value, String description) {
        if (value == null || value.isBlank()) throw new ProveedorPagoException("Falta " + description);
        return value.trim();
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new ProveedorPagoException("El monto de la transacción no es válido para Izipay");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String firstText(Map<String, Object> source, String... keys) {
        if (source == null) return null;
        for (String key : keys) {
            String value = text(source, key);
            if (value != null) return value;
        }
        return null;
    }

    private String text(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private int positiveInt(Object value, int fallback) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return parsed > 0 ? (parsed > 60 ? Math.max(1, parsed / 60) : parsed) : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
