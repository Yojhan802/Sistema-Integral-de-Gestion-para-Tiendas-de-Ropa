package com.freestyleperu.aplicacion.pago.service;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransaction;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransactionStatus;
import com.freestyleperu.aplicacion.pago.exception.WebhookFirmaException;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData;
import com.freestyleperu.aplicacion.pago.repository.PaymentTransactionRepository;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Procesa eventos de Culqi verificando el evento contra la API autenticada.
 * El payload recibido por HTTP nunca se considera suficiente para aprobar un
 * pago.
 */
@Service
public class CulqiWebhookService {

    private static final String DEFAULT_API_URL = "https://api.culqi.com/v2";
    private static final String SUCCESS_EVENT = "charge.creation.succeeded";
    private static final String FAILED_EVENT = "charge.creation.failed";

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentProviderConfigurationService configurationService;
    private final PaymentTransactionService transactionService;
    private final ObjectMapper objectMapper;

    public CulqiWebhookService(
            PaymentTransactionRepository transactionRepository,
            PaymentProviderConfigurationService configurationService,
            PaymentTransactionService transactionService,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.configurationService = configurationService;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    public void procesar(String rawNotification) {
        JsonNode notification;
        try {
            notification = objectMapper.readTree(rawNotification);
        } catch (Exception ex) {
            throw new WebhookFirmaException("La notificación de Culqi no contiene JSON válido");
        }
        String eventId = text(notification, "id");
        String notificationType = text(notification, "type");
        if (eventId == null || notificationType == null) {
            throw new WebhookFirmaException("La notificación de Culqi no contiene id y type");
        }
        if (!SUCCESS_EVENT.equals(notificationType) && !FAILED_EVENT.equals(notificationType)) {
            return;
        }

        PaymentProviderConfigurationData configuration = configurationService
                .obtenerParaBackend(PaymentProviderType.CULQI)
                .orElseThrow(() -> new WebhookFirmaException("La configuración de Culqi no está disponible"));
        JsonNode verifiedEvent = consultarEvento(eventId, configuration);
        if (verifiedEvent == null || !verifiedEvent.isObject()
                || !eventId.equals(text(verifiedEvent, "id"))
                || !notificationType.equals(text(verifiedEvent, "type"))) {
            throw new WebhookFirmaException("El evento verificado de Culqi no coincide con la notificación");
        }

        JsonNode charge = verifiedEvent.path("data");
        if (!charge.isObject()) {
            throw new WebhookFirmaException("El evento verificado de Culqi no contiene el cargo");
        }
        PaymentTransaction transaction = localizarTransaccion(charge);
        validarCargo(transaction, charge);

        String providerTransactionId = text(charge, "id");
        PaymentTransactionStatus status = SUCCESS_EVENT.equals(notificationType)
                ? PaymentTransactionStatus.APPROVED
                : PaymentTransactionStatus.DECLINED;
        transactionService.registrarResultado(
                transaction.getId(), PaymentProviderType.CULQI, status, providerTransactionId,
                text(charge, "reference_code"),
                status == PaymentTransactionStatus.DECLINED ? firstNonBlank(
                        text(charge, "decline_code"), text(charge, "code")) : null,
                status == PaymentTransactionStatus.DECLINED ? firstNonBlank(
                        text(charge, "user_message"), text(charge, "merchant_message")) : null);
    }

    private JsonNode consultarEvento(String eventId, PaymentProviderConfigurationData configuration) {
        String secretKey = credential(configuration, "secretKey", "privateKey", "apiKey");
        if (secretKey == null) {
            throw new WebhookFirmaException("Falta la llave privada de Culqi para verificar el evento");
        }
        try {
            return RestClient.builder()
                    .baseUrl(apiUrl(configuration))
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/events/{id}").build(eventId))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            throw new WebhookFirmaException("No se pudo verificar el evento de Culqi");
        }
    }

    private PaymentTransaction localizarTransaccion(JsonNode charge) {
        String transactionId = text(charge.path("metadata"), "payment_transaction_id");
        if (transactionId != null) {
            try {
                return transactionRepository.findById(Long.valueOf(transactionId))
                        .orElseThrow(() -> RecursoNoEncontradoException.de(
                                "Transacción de pago", Long.valueOf(transactionId)));
            } catch (NumberFormatException ex) {
                throw new WebhookFirmaException("La metadata de Culqi contiene una transacción inválida");
            }
        }
        String providerTransactionId = text(charge, "id");
        if (providerTransactionId != null) {
            return transactionRepository.findFirstByProviderAndProviderTransactionId(
                            PaymentProviderType.CULQI, providerTransactionId)
                    .orElseThrow(() -> new WebhookFirmaException(
                            "El cargo de Culqi no corresponde a una transacción registrada"));
        }
        throw new WebhookFirmaException("El evento de Culqi no identifica la transacción");
    }

    private void validarCargo(PaymentTransaction transaction, JsonNode charge) {
        if (transaction.getProvider() != PaymentProviderType.CULQI) {
            throw new WebhookFirmaException("El cargo no corresponde a Culqi");
        }
        String providerTransactionId = text(charge, "id");
        if (providerTransactionId == null) {
            throw new WebhookFirmaException("El cargo de Culqi no contiene identificador");
        }
        String orderNumber = text(charge.path("metadata"), "order_number");
        if (orderNumber != null && transaction.getOrder() != null
                && !orderNumber.equals(transaction.getOrder().getOrderNumber())) {
            throw new WebhookFirmaException("El pedido del cargo de Culqi no coincide con la transacción");
        }
        try {
            BigDecimal amountInCents = new BigDecimal(text(charge, "amount"));
            BigDecimal expectedInCents = transaction.getAmount().movePointRight(2);
            if (expectedInCents.compareTo(amountInCents) != 0) {
                throw new WebhookFirmaException("El monto del cargo de Culqi no coincide con la transacción");
            }
        } catch (NumberFormatException | NullPointerException ex) {
            throw new WebhookFirmaException("El monto del cargo de Culqi no es válido");
        }
        String currency = text(charge, "currency_code");
        if (currency == null || !transaction.getCurrencyCode().equalsIgnoreCase(currency)) {
            throw new WebhookFirmaException("La moneda del cargo de Culqi no coincide con la transacción");
        }
    }

    private String apiUrl(PaymentProviderConfigurationData configuration) {
        String configured = configuration.apiUrl();
        String url = configured == null || configured.isBlank() ? DEFAULT_API_URL : configured.trim();
        if (configuration.environment() == PaymentProviderEnvironment.PRODUCTION && !url.startsWith("https://")) {
            throw new WebhookFirmaException("La URL de Culqi en producción debe usar HTTPS");
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

    private String text(JsonNode node, String field) {
        if (node == null || node.get(field) == null || node.get(field).isNull()) return null;
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
