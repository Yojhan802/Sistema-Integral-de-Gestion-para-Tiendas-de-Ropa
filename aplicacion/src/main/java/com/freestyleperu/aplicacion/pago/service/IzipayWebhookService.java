package com.freestyleperu.aplicacion.pago.service;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransaction;
import com.freestyleperu.aplicacion.pago.domain.PaymentTransactionStatus;
import com.freestyleperu.aplicacion.pago.exception.WebhookFirmaException;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData;
import com.freestyleperu.aplicacion.pago.repository.PaymentTransactionRepository;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Procesa el IPN de Izipay sin confiar en el callback del navegador. */
@Service
public class IzipayWebhookService {

    private final PaymentTransactionRepository transactionRepository;
    private final PaymentProviderConfigurationService configurationService;
    private final PaymentTransactionService transactionService;
    private final ObjectMapper objectMapper;

    public IzipayWebhookService(
            PaymentTransactionRepository transactionRepository,
            PaymentProviderConfigurationService configurationService,
            PaymentTransactionService transactionService,
            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.configurationService = configurationService;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void procesar(String transactionId, String headerTransactionId, String rawBody) {
        JsonNode body;
        try {
            body = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new WebhookFirmaException("El callback de Izipay no contiene JSON válido");
        }
        String correlationId = required(transactionId, "transactionId");
        PaymentTransaction transaction = buscarTransaccion(correlationId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("TransacciÃ³n de pago", transactionId));
        if (transaction.getProvider() != PaymentProviderType.IZIPAY) {
            throw new WebhookFirmaException("El callback no corresponde a Izipay");
        }

        PaymentProviderConfigurationData configuration = configurationService
                .obtenerParaBackend(PaymentProviderType.IZIPAY)
                .orElseThrow(() -> new WebhookFirmaException("La configuraciÃ³n de Izipay no estÃ¡ disponible"));

        String callbackTransactionId = text(body, "transactionId");
        if (!correlationId.equals(headerTransactionId) || !correlationId.equals(callbackTransactionId)) {
            throw new WebhookFirmaException("El identificador del callback no coincide con la transacciÃ³n");
        }

        String code = text(body, "code");
        String payloadHttp = text(body, "payloadHttp");
        String signature = text(body, "signature");
        if (code == null || payloadHttp == null) {
            throw new WebhookFirmaException("El callback de Izipay estÃ¡ incompleto");
        }
        if (!"021".equals(code) && !"COMMUNICATION_ERROR".equals(code)
                && !firmaValida(payloadHttp, signature, claveHash(configuration))) {
            throw new WebhookFirmaException("La firma del callback de Izipay no es vÃ¡lida");
        }

        JsonNode order = body.path("response").path("order");
        JsonNode firstOrder = order.isArray() && !order.isEmpty() ? order.get(0) : null;
        validarOrden(transaction, firstOrder);

        PaymentTransactionStatus status = "00".equals(code)
                ? PaymentTransactionStatus.APPROVED
                : PaymentTransactionStatus.DECLINED;
        String reference = firstOrder == null ? null : firstNonBlank(
                text(firstOrder, "referenceNumber"), text(firstOrder, "uniqueId"));
        transactionService.registrarResultado(
                transaction.getId(), PaymentProviderType.IZIPAY, status, callbackTransactionId, reference,
                "00".equals(code) ? null : code, text(body, "messageUser"));
    }

    private Optional<PaymentTransaction> buscarTransaccion(String correlationId) {
        try {
            Optional<PaymentTransaction> byId = transactionRepository.findById(Long.valueOf(correlationId));
            if (byId.isPresent()) return byId;
        } catch (NumberFormatException ignored) {
            // El transactionId externo normalmente no es numérico.
        }
        return transactionRepository.findFirstByProviderAndProviderTransactionId(
                PaymentProviderType.IZIPAY, correlationId);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new WebhookFirmaException("Falta el identificador " + field + " de Izipay");
        }
        return value.trim();
    }

    private void validarOrden(PaymentTransaction transaction, JsonNode order) {
        if (order == null) {
            throw new WebhookFirmaException("El callback de Izipay no contiene la orden");
        }
        if (!transaction.getOrder().getOrderNumber().equals(text(order, "orderNumber"))) {
            throw new WebhookFirmaException("La orden del callback no coincide con la transacciÃ³n");
        }
        try {
            if (transaction.getAmount().compareTo(new BigDecimal(text(order, "amount"))) != 0) {
                throw new WebhookFirmaException("El monto del callback no coincide con la transacciÃ³n");
            }
        } catch (NumberFormatException | NullPointerException ex) {
            throw new WebhookFirmaException("El monto del callback de Izipay no es vÃ¡lido");
        }
        String currency = text(order, "currency");
        if (currency != null && !transaction.getCurrencyCode().equalsIgnoreCase(currency)) {
            throw new WebhookFirmaException("La moneda del callback no coincide con la transacciÃ³n");
        }
    }

    private boolean firmaValida(String payload, String signature, String keyHash) {
        if (signature == null || keyHash == null || keyHash.isBlank()) return false;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(keyHash.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] actual = Base64.getDecoder().decode(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ex) {
            return false;
        }
    }

    private String claveHash(PaymentProviderConfigurationData configuration) {
        return firstNonBlank(configuration.credentials().get("hashKey"),
                configuration.credentials().get("keyHash"));
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
