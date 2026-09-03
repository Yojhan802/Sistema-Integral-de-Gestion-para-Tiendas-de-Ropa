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
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Adaptador para Pago Web desacoplado de Niubiz (VisaNet). */
@Component
public class NiubizProvider implements PaymentProvider {

    private static final String TEST_API_URL = "https://apisandbox.vnforappstest.com";
    private static final String PRODUCTION_API_URL = "https://apiprod.vnforapps.com";
    private static final String TEST_SCRIPT_URL = "https://static-content-qas.vnforapps.com/v2/js/checkout.js?qa=true";
    private static final String PRODUCTION_SCRIPT_URL = "https://static-content.vnforapps.com/v2/js/checkout.js";

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.NIUBIZ;
    }

    @Override
    public PaymentProviderCheckoutResult initializeCheckout(
            PaymentProviderCheckoutCommand command, PaymentProviderConfigurationData configuration) {
        String merchantCode = required(configuration.merchantCode(), "el código de comercio de Niubiz");
        String username = credential(configuration, "username", "user", "integrationUser");
        String password = credential(configuration, "password", "integrationPassword", "secret");
        if (username == null || password == null) {
            throw new ProveedorPagoException("Faltan usuario y contraseña de integración de Niubiz");
        }
        validateAmount(command.amount());

        String securityToken = requestSecurityToken(configuration, username, password);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", command.amount());
        body.put("channel", "web");
        Map<String, Object> antifraud = new LinkedHashMap<>();
        Map<String, Object> merchantData = new LinkedHashMap<>();
        putIfPresent(merchantData, "MDD4", command.customerEmail());
        putIfPresent(merchantData, "MDD30", command.customerDocument());
        antifraud.put("merchantDefineData", merchantData);
        body.put("antifraud", antifraud);

        try {
            ResponseEntity<Map> response = restClient().post()
                    .uri(endpoint(configuration, "sessionUrl", "/api.ecommerce/v2/ecommerce/token/session/" + merchantCode))
                    .header("Authorization", securityToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
            Map<String, Object> responseBody = response.getBody();
            String sessionToken = firstText(responseBody, "sessionKey", "sessionToken", "sessiontoken");
            if (sessionToken == null) {
                throw new ProveedorPagoException("Niubiz no devolvió la clave de sesión del checkout");
            }
            int expirationMinutes = positiveInt(responseBody == null ? null : responseBody.get("expirationTime"), 20);
            return new PaymentProviderCheckoutResult(
                    PaymentProviderType.NIUBIZ,
                    sessionToken,
                    scriptUrl(configuration),
                    merchantCode,
                    null,
                    null,
                    expirationMinutes);
        } catch (RestClientException ex) {
            throw new ProveedorPagoException("No se pudo crear la sesión de pago de Niubiz");
        }
    }

    @Override
    public PaymentProviderChargeResult charge(
            PaymentProviderChargeCommand command, PaymentProviderConfigurationData configuration) {
        String merchantCode = required(configuration.merchantCode(), "el código de comercio de Niubiz");
        String username = credential(configuration, "username", "user", "integrationUser");
        String password = credential(configuration, "password", "integrationPassword", "secret");
        if (username == null || password == null) {
            throw new ProveedorPagoException("Faltan usuario y contraseña de integración de Niubiz");
        }
        if (command.sourceId() == null || command.sourceId().isBlank()) {
            throw new ProveedorPagoException("Niubiz no recibió el token de transacción");
        }
        validateAmount(command.amount());

        try {
            String securityToken = requestSecurityToken(configuration, username, password);
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("tokenId", command.sourceId().trim());
            order.put("purchaseNumber", command.orderNumber());
            order.put("amount", command.amount());
            order.put("currency", command.currencyCode());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("channel", "web");
            body.put("captureType", "automatic");
            body.put("countable", true);
            body.put("order", order);

            ResponseEntity<Map> response = restClient().post()
                    .uri(endpoint(configuration, "authorizationUrl", "/api.authorization/v3/authorization/ecommerce/" + merchantCode))
                    .header("Authorization", securityToken)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);
            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> responseOrder = map(responseBody, "order");
            Map<String, Object> dataMap = map(responseBody, "dataMap");
            String actionCode = firstText(responseOrder, "actionCode");
            if (actionCode == null) actionCode = firstText(dataMap, "ACTION_CODE");
            String actionDescription = firstText(responseOrder, "actionDescription");
            if (actionDescription == null) actionDescription = firstText(dataMap, "ACTION_DESCRIPTION");
            String providerId = firstText(responseOrder, "transactionId");
            if (providerId == null) providerId = firstText(dataMap, "TRANSACTION_ID", "ID_UNICO");
            String reference = firstText(responseOrder, "authorizationCode");
            if (reference == null) reference = firstText(dataMap, "AUTHORIZATION_CODE");

            if ("000".equals(actionCode)) {
                return new PaymentProviderChargeResult(
                        PaymentTransactionStatus.APPROVED, providerId, reference, null, null);
            }
            if (actionCode == null) {
                return new PaymentProviderChargeResult(
                        PaymentTransactionStatus.FAILED, providerId, reference, "MISSING_ACTION_CODE",
                        "Niubiz no devolvió el código de resultado de la autorización");
            }
            return new PaymentProviderChargeResult(
                    PaymentTransactionStatus.DECLINED, providerId, reference, actionCode,
                    actionDescription == null ? "Niubiz rechazó la operación" : actionDescription);
        } catch (ProveedorPagoException ex) {
            throw ex;
        } catch (RestClientException ex) {
            return new PaymentProviderChargeResult(
                    PaymentTransactionStatus.FAILED, null, null, "PROVIDER_REQUEST_FAILED",
                    "No se pudo comunicar con Niubiz en este momento");
        }
    }

    private String requestSecurityToken(
            PaymentProviderConfigurationData configuration, String username, String password) {
        String method = credential(configuration, "securityMethod");
        try {
            String token;
            if ("GET".equalsIgnoreCase(method)) {
                token = securityGet(configuration, username, password);
            } else {
                try {
                    token = securityPost(configuration, username, password);
                } catch (HttpClientErrorException.MethodNotAllowed ex) {
                    // Algunas cuentas antiguas de Niubiz todavía exponen GET.
                    token = securityGet(configuration, username, password);
                }
            }
            token = clean(token);
            if (token == null || "Unauthorized access".equalsIgnoreCase(token)) {
                throw new ProveedorPagoException("Niubiz rechazó las credenciales de integración");
            }
            return token;
        } catch (ProveedorPagoException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ProveedorPagoException("No se pudo obtener el token de seguridad de Niubiz");
        }
    }

    private String securityPost(PaymentProviderConfigurationData configuration, String username, String password) {
        return restClient().post()
                .uri(endpoint(configuration, "securityUrl", "/api.security/v1/security"))
                .headers(headers -> headers.setBasicAuth(username, password))
                .header("Content-Type", "application/json")
                .retrieve()
                .body(String.class);
    }

    private String securityGet(PaymentProviderConfigurationData configuration, String username, String password) {
        return restClient().get()
                .uri(endpoint(configuration, "securityUrl", "/api.security/v1/security"))
                .headers(headers -> headers.setBasicAuth(username, password))
                .header("Content-Type", "application/json")
                .retrieve()
                .body(String.class);
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
            throw new ProveedorPagoException("La URL de Niubiz en producción debe usar HTTPS");
        }
        return url.replaceAll("/+$", "");
    }

    private String scriptUrl(PaymentProviderConfigurationData configuration) {
        String configured = credential(configuration, "checkoutScriptUrl", "scriptUrl");
        if (configured != null) return configured;
        return configuration.environment() == PaymentProviderEnvironment.PRODUCTION
                ? PRODUCTION_SCRIPT_URL : TEST_SCRIPT_URL;
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
            throw new ProveedorPagoException("El monto de la transacción no es válido para Niubiz");
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private String firstText(Map<String, Object> source, String... keys) {
        if (source == null) return null;
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }

    private int positiveInt(Object value, int fallback) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed <= 0) return fallback;
            return parsed > 60 ? Math.max(1, parsed / 60) : parsed;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String clean = value.trim();
        if (clean.length() >= 2 && clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean.isBlank() ? null : clean;
    }
}
