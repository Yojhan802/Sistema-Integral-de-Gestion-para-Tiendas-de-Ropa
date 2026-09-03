package com.freestyleperu.aplicacion.facturacion.provider;

import com.freestyleperu.aplicacion.facturacion.domain.BillingProvider;
import com.freestyleperu.aplicacion.facturacion.domain.BillingProviderEnvironment;
import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentStatus;
import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentType;
import com.freestyleperu.aplicacion.facturacion.exception.ProveedorFacturacionException;
import com.freestyleperu.aplicacion.facturacion.port.BillingConfigurationData;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingCommand;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingProvider;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingResult;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingResource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/** Adaptador basado exclusivamente en el contrato OpenAPI publicado por Verifac. */
@Component
public class VerifacProvider implements ElectronicInvoicingProvider {

    private static final String DEFAULT_API_URL = "https://api.verifac.pe";

    private final ObjectMapper objectMapper;

    public VerifacProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public BillingProvider type() {
        return BillingProvider.VERIFACT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ElectronicInvoicingResult issue(
            ElectronicInvoicingCommand command, BillingConfigurationData configuration) {
        String apiKey = credential(configuration, "apiKey", "apiToken", "token");
        if (apiKey == null) {
            throw new ProveedorFacturacionException("Falta la API key de Verifac en la configuración de la empresa");
        }

        Map<String, Object> snapshot = parse(command.payloadJson());
        Map<String, Object> request = buildRequest(command, snapshot);
        String path = switch (command.documentType()) {
            case FACTURA -> "/api/v1/comprobantes/facturas";
            case BOLETA -> "/api/v1/comprobantes/boletas";
            case NOTA_CREDITO -> "/api/v1/comprobantes/notas-credito";
            case NOTA_DEBITO -> "/api/v1/comprobantes/notas-debito";
        };

        try {
            ResponseEntity<Map> response = client(configuration)
                    .post()
                    .uri(path)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-API-Key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .toEntity(Map.class);
            return parseSuccess(response.getBody(), response.getStatusCode().value());
        } catch (RestClientResponseException ex) {
            return new ElectronicInvoicingResult(
                    ex.getStatusCode().is4xxClientError() ? ElectronicDocumentStatus.REJECTED : ElectronicDocumentStatus.ERROR,
                    null, null, null, null, String.valueOf(ex.getStatusCode().value()),
                    "Verifac rechazó o no pudo procesar el comprobante", null, null);
        } catch (RestClientException ex) {
            return new ElectronicInvoicingResult(
                    ElectronicDocumentStatus.ERROR, null, null, null, null, "PROVIDER_REQUEST_FAILED",
                    "No se pudo comunicar con Verifac en este momento", null, null);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ElectronicInvoicingResult fetchStatus(String providerDocumentId, BillingConfigurationData configuration) {
        try {
            ResponseEntity<Map> response = authorizedClient(configuration)
                    .get()
                    .uri("/api/v1/comprobantes/{id}/cdr", providerDocumentId)
                    .retrieve()
                    .toEntity(Map.class);
            return parseSuccess(response.getBody(), response.getStatusCode().value(), providerDocumentId);
        } catch (RestClientResponseException ex) {
            throw new ProveedorFacturacionException("Verifac no pudo consultar el estado del comprobante");
        } catch (RestClientException ex) {
            throw new ProveedorFacturacionException("No se pudo consultar el estado en Verifac");
        }
    }

    @Override
    public ElectronicInvoicingResult retry(String providerDocumentId, BillingConfigurationData configuration) {
        try {
            ResponseEntity<Map> response = authorizedClient(configuration)
                    .post()
                    .uri("/api/v1/comprobantes/{id}/reenviar", providerDocumentId)
                    .retrieve()
                    .toEntity(Map.class);
            if (response.getStatusCode().value() != 202) {
                throw new ProveedorFacturacionException("Verifac no aceptó el reenvío del comprobante");
            }
            return new ElectronicInvoicingResult(
                    ElectronicDocumentStatus.PENDING, providerDocumentId, "EN_COLA", null, null,
                    null, null, null, null);
        } catch (RestClientResponseException ex) {
            throw new ProveedorFacturacionException("Verifac no aceptó el reenvío del comprobante");
        } catch (RestClientException ex) {
            throw new ProveedorFacturacionException("No se pudo reenviar el comprobante a Verifac");
        }
    }

    @Override
    public ElectronicInvoicingResource download(
            String providerDocumentId, String resource, BillingConfigurationData configuration) {
        String path = switch (resource) {
            case "pdf" -> "/api/v1/comprobantes/{id}/pdf";
            case "xml" -> "/api/v1/comprobantes/{id}/xml";
            case "cdr" -> "/api/v1/comprobantes/{id}/cdr";
            default -> throw new ProveedorFacturacionException("Recurso de comprobante no soportado");
        };
        try {
            ResponseEntity<byte[]> response = authorizedClient(configuration)
                    .get()
                    .uri(path, providerDocumentId)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] content = response.getBody();
            if (content == null || content.length == 0) {
                throw new ProveedorFacturacionException("Verifac no devolvió el recurso solicitado");
            }
            String contentType = switch (resource) {
                case "pdf" -> "application/pdf";
                case "xml" -> "application/xml";
                default -> "application/zip";
            };
            String extension = switch (resource) {
                case "pdf" -> ".pdf";
                case "xml" -> ".xml";
                default -> ".zip";
            };
            return new ElectronicInvoicingResource(content, contentType, "comprobante-" + providerDocumentId + extension);
        } catch (RestClientResponseException ex) {
            throw new ProveedorFacturacionException("Verifac no pudo descargar el recurso solicitado");
        } catch (RestClientException ex) {
            throw new ProveedorFacturacionException("No se pudo descargar el recurso desde Verifac");
        }
    }

    private Map<String, Object> buildRequest(ElectronicInvoicingCommand command, Map<String, Object> snapshot) {
        BigDecimal total = decimal(snapshot.get("total"));
        BigDecimal rate = decimal(snapshot.get("igvRate"));
        BigDecimal divisor = BigDecimal.ONE.add(rate);
        Map<String, Object> customer = map(snapshot.get("customer"));
        Map<String, Object> note = map(snapshot.get("note"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("serie", command.series());
        request.put("fechaEmision", LocalDate.now().toString());
        request.put("moneda", text(snapshot.get("currencyCode"), "PEN"));
        request.put("items", buildItems(snapshot, divisor, rate));
        request.put("totalGravadas", taxable(total, divisor));
        request.put("totalExoneradas", BigDecimal.ZERO);
        request.put("totalInafectas", BigDecimal.ZERO);
        request.put("igv", total.subtract(taxable(total, divisor)).setScale(2, RoundingMode.HALF_UP));
        request.put("totalDescuentos", decimal(snapshot.get("discountAmount")));
        request.put("total", total);
        request.put("formaPago", "CONTADO");
        request.put("observaciones", text(snapshot.get("saleNumber"), null));

        if (command.documentType() == ElectronicDocumentType.NOTA_CREDITO
                || command.documentType() == ElectronicDocumentType.NOTA_DEBITO) {
            String referenceType = text(note.get("sourceDocumentTypeCode"), null);
            String reference = text(note.get("sourceSeriesNumber"), null);
            String reasonCode = text(note.get("reasonCode"), null);
            String reasonDescription = text(note.get("reasonDescription"), null);
            if (referenceType == null || reference == null || reasonCode == null || reasonDescription == null) {
                throw new ProveedorFacturacionException("La nota no contiene referencia y motivo fiscal completos");
            }
            request.put("tipoComprobanteReferencia", referenceType);
            request.put("serieNumeroReferencia", reference);
            request.put("codigoMotivo", reasonCode);
            request.put("descripcionMotivo", reasonDescription);
        }

        if (command.documentType() == ElectronicDocumentType.FACTURA
                || customer.get("docNumber") != null) {
            request.put("tipoDocAdquirente", customerDocumentType(text(customer.get("docType"), null)));
            request.put("numDocAdquirente", text(customer.get("docNumber"), ""));
            request.put("razonSocialAdquirente", text(customer.get("fullName"), ""));
        }
        return request;
    }

    private List<Map<String, Object>> buildItems(Map<String, Object> snapshot, BigDecimal divisor, BigDecimal rate) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> lines = listOfMaps(snapshot.get("lines"));
        int itemNumber = 1;
        for (Map<String, Object> line : lines) {
            BigDecimal quantity = decimal(line.get("quantity"));
            BigDecimal unitPrice = decimal(line.get("unitPrice"));
            BigDecimal saleValue = decimal(line.get("subtotal")).divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal igv = decimal(line.get("subtotal")).subtract(saleValue).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("numeroItem", itemNumber++);
            item.put("codigoProducto", text(line.get("sku"), "ITEM"));
            item.put("descripcion", text(line.get("description"), "Producto"));
            item.put("unidadMedida", "NIU");
            item.put("cantidad", quantity);
            item.put("valorUnitario", unitPrice.divide(divisor, 2, RoundingMode.HALF_UP));
            item.put("precioUnitario", unitPrice);
            item.put("valorVenta", saleValue);
            item.put("igv", igv);
            item.put("codigoAfectacionIgv", rate.signum() > 0 ? "10" : "20");
            item.put("descuento", decimal(line.get("discountAmount")));
            result.add(item);
        }
        BigDecimal shipping = decimal(snapshot.get("shippingAmount"));
        if (shipping.signum() > 0) {
            BigDecimal saleValue = shipping.divide(divisor, 2, RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("numeroItem", itemNumber);
            item.put("codigoProducto", "ENVIO");
            item.put("descripcion", "Envío");
            item.put("unidadMedida", "NIU");
            item.put("cantidad", BigDecimal.ONE);
            item.put("valorUnitario", saleValue);
            item.put("precioUnitario", shipping);
            item.put("valorVenta", saleValue);
            item.put("igv", shipping.subtract(saleValue).setScale(2, RoundingMode.HALF_UP));
            item.put("codigoAfectacionIgv", rate.signum() > 0 ? "10" : "20");
            item.put("descuento", BigDecimal.ZERO);
            result.add(item);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private ElectronicInvoicingResult parseSuccess(Map<String, Object> body, int status) {
        return parseSuccess(body, status, null);
    }

    @SuppressWarnings("unchecked")
    private ElectronicInvoicingResult parseSuccess(
            Map<String, Object> body, int status, String existingProviderDocumentId) {
        Map<String, Object> data = body == null
                ? Map.of()
                : body.get("data") instanceof Map<?, ?> ? map(body.get("data")) : body;
        String id = text(data.get("id"), text(data.get("comprobanteId"),
                text(data.get("uuid"), text(data.get("documentId"), existingProviderDocumentId))));
        String state = text(data.get("estado"), text(data.get("status"), null));
        String cdrMessage = text(data.get("descripcionCdr"),
                text(data.get("cdr"), text(body == null ? null : body.get("message"), null)));
        String xmlPath = text(data.get("xmlPath"), text(data.get("xml"), null));
        String cdrPath = text(data.get("cdrPath"), null);
        if (status != 202 || id == null) {
            return new ElectronicInvoicingResult(
                    statusFrom(state), id, state,
                    text(data.get("serie"), null), text(data.get("numero"), null),
                    text(data.get("codigoCdr"), null), cdrMessage, xmlPath, cdrPath);
        }
        return new ElectronicInvoicingResult(
                statusFrom(text(state, "PENDIENTE")), id, text(state, "PENDIENTE"),
                text(data.get("serie"), null), text(data.get("numero"), null),
                text(data.get("codigoCdr"), null), cdrMessage, xmlPath, cdrPath);
    }

    private RestClient client(BillingConfigurationData configuration) {
        String url = apiUrl(configuration);
        return RestClient.builder().baseUrl(url).build();
    }

    private String apiUrl(BillingConfigurationData configuration) {
        if (configuration.environment() == BillingProviderEnvironment.TEST
                && (configuration.apiUrl() == null || configuration.apiUrl().isBlank())) {
            throw new ProveedorFacturacionException("La URL del sandbox de Verifac es obligatoria en ambiente de pruebas");
        }
        String url = configuration.apiUrl() == null || configuration.apiUrl().isBlank()
                ? DEFAULT_API_URL : configuration.apiUrl().trim();
        if (configuration.environment() == BillingProviderEnvironment.PRODUCTION && !url.startsWith("https://")) {
            throw new ProveedorFacturacionException("La URL de Verifac en producción debe usar HTTPS");
        }
        return url.replaceAll("/+$", "");
    }

    private RestClient authorizedClient(BillingConfigurationData configuration) {
        String apiKey = credential(configuration, "apiKey", "apiToken", "token");
        if (apiKey == null) {
            throw new ProveedorFacturacionException("Falta la API key de Verifac en la configuración de la empresa");
        }
        return RestClient.builder()
                .baseUrl(apiUrl(configuration))
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private String credential(BillingConfigurationData configuration, String... keys) {
        for (String key : keys) {
            String value = configuration.credentials().get(key);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new ProveedorFacturacionException("El snapshot del comprobante no es válido");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList();
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ex) { return BigDecimal.ZERO; }
    }

    private BigDecimal taxable(BigDecimal total, BigDecimal divisor) {
        return total.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String customerDocumentType(String type) {
        return switch (type) {
            case "RUC" -> "6";
            case "DNI" -> "1";
            case "CE" -> "4";
            default -> "0";
        };
    }

    private ElectronicDocumentStatus statusFrom(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "ACEPTADO" -> ElectronicDocumentStatus.ACCEPTED;
            case "RECHAZADO" -> ElectronicDocumentStatus.REJECTED;
            case "ERROR" -> ElectronicDocumentStatus.ERROR;
            case "ENVIADO" -> ElectronicDocumentStatus.SENT;
            default -> ElectronicDocumentStatus.PENDING;
        };
    }
}
