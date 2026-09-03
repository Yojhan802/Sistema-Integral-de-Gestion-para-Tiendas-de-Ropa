package com.freestyleperu.aplicacion.facturacion.provider;

import com.freestyleperu.aplicacion.facturacion.domain.BillingProvider;
import com.freestyleperu.aplicacion.facturacion.domain.BillingProviderEnvironment;
import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentStatus;
import com.freestyleperu.aplicacion.facturacion.domain.ElectronicDocumentType;
import com.freestyleperu.aplicacion.facturacion.exception.ProveedorFacturacionException;
import com.freestyleperu.aplicacion.facturacion.port.BillingConfigurationData;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingCommand;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingProvider;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingResource;
import com.freestyleperu.aplicacion.facturacion.port.ElectronicInvoicingResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador para la API JSON de NubeFact.
 *
 * NubeFact usa una ruta única por cuenta y recibe todas las operaciones mediante
 * POST. A diferencia de Verifac, la API exige que la aplicación envíe el número
 * correlativo y devuelve los archivos como ZIP en base64.
 */
@Component
public class NubeFactProvider implements ElectronicInvoicingProvider {

    private static final String AUTHORIZATION_PREFIX = "Token token=";
    private static final DateTimeFormatter NUBEFACT_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ObjectMapper objectMapper;

    public NubeFactProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public BillingProvider type() {
        return BillingProvider.NUBEFACT;
    }

    @Override
    public ElectronicInvoicingResult issue(
            ElectronicInvoicingCommand command, BillingConfigurationData configuration) {
        if (command.documentNumber() == null || command.documentNumber().isBlank()) {
            throw new ProveedorFacturacionException("NubeFact requiere un número correlativo para emitir");
        }
        Map<String, Object> snapshot = parse(command.payloadJson());
        Map<String, Object> request = buildRequest(command, snapshot);
        try {
            ResponseEntity<Map> response = client(configuration)
                    .post()
                    .uri("")
                    .body(request)
                    .retrieve()
                    .toEntity(Map.class);
            return parseResult(response.getBody(), response.getStatusCode().value(), command);
        } catch (RestClientResponseException ex) {
            return new ElectronicInvoicingResult(
                    ex.getStatusCode().is4xxClientError()
                            ? ElectronicDocumentStatus.REJECTED : ElectronicDocumentStatus.ERROR,
                    providerId(typeCode(command.documentType()), command.series(), command.documentNumber()),
                    "HTTP_" + ex.getStatusCode().value(), command.series(), command.documentNumber(),
                    String.valueOf(ex.getStatusCode().value()),
                    "NubeFact rechazó o no pudo procesar el comprobante", null, null);
        } catch (RestClientException ex) {
            return new ElectronicInvoicingResult(
                    ElectronicDocumentStatus.ERROR,
                    providerId(typeCode(command.documentType()), command.series(), command.documentNumber()),
                    "PROVIDER_REQUEST_FAILED", command.series(), command.documentNumber(),
                    "PROVIDER_REQUEST_FAILED", "No se pudo comunicar con NubeFact en este momento", null, null);
        }
    }

    @Override
    public ElectronicInvoicingResult fetchStatus(
            String providerDocumentId, BillingConfigurationData configuration) {
        DocumentReference reference = parseProviderId(providerDocumentId);
        try {
            ResponseEntity<Map> response = client(configuration)
                    .post()
                    .uri("")
                    .body(queryRequest(reference))
                    .retrieve()
                    .toEntity(Map.class);
            return parseResult(response.getBody(), response.getStatusCode().value(), reference);
        } catch (RestClientResponseException ex) {
            throw new ProveedorFacturacionException("NubeFact no pudo consultar el comprobante");
        } catch (RestClientException ex) {
            throw new ProveedorFacturacionException("No se pudo consultar el estado en NubeFact");
        }
    }

    @Override
    public ElectronicInvoicingResult retry(
            ElectronicInvoicingCommand command, String providerDocumentId,
            BillingConfigurationData configuration) {
        return issue(command, configuration);
    }

    @Override
    public ElectronicInvoicingResult retry(
            String providerDocumentId, BillingConfigurationData configuration) {
        throw new ProveedorFacturacionException(
                "NubeFact requiere el snapshot completo del comprobante para reintentar");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ElectronicInvoicingResource download(
            String providerDocumentId, String resource, BillingConfigurationData configuration) {
        String field = switch (resource) {
            case "pdf" -> "pdf_zip_base64";
            case "xml" -> "xml_zip_base64";
            case "cdr" -> "cdr_zip_base64";
            default -> throw new ProveedorFacturacionException("Recurso de comprobante no soportado");
        };
        DocumentReference reference = parseProviderId(providerDocumentId);
        try {
            ResponseEntity<Map> response = client(configuration)
                    .post()
                    .uri("")
                    .body(queryRequest(reference))
                    .retrieve()
                    .toEntity(Map.class);
            Map<String, Object> invoice = invoice(response.getBody());
            String encoded = text(invoice.get(field), null);
            if (encoded != null && !encoded.isBlank()) {
                byte[] content = unpack(Base64.getDecoder().decode(stripDataPrefix(encoded)), resource);
                String contentType = resource.equals("pdf") ? "application/pdf"
                        : resource.equals("xml") ? "application/xml" : "application/zip";
                String extension = resource.equals("pdf") ? ".pdf"
                        : resource.equals("xml") ? ".xml" : ".zip";
                return new ElectronicInvoicingResource(content, contentType,
                        "comprobante-" + reference.series() + "-" + reference.number() + extension);
            }

            String link = text(invoice.get("enlace_del_" + resource), null);
            if (link != null && link.startsWith("https://")) {
                ResponseEntity<byte[]> file = RestClient.create().get().uri(link).retrieve().toEntity(byte[].class);
                if (file.getBody() != null && file.getBody().length > 0) {
                    return new ElectronicInvoicingResource(file.getBody(),
                            resource.equals("pdf") ? "application/pdf" : "application/xml",
                            "comprobante-" + reference.series() + "-" + reference.number() + "." + resource);
                }
            }
            throw new ProveedorFacturacionException("NubeFact no devolvió el recurso solicitado");
        } catch (IllegalArgumentException ex) {
            throw new ProveedorFacturacionException("El recurso recibido de NubeFact no es base64 válido");
        } catch (RestClientResponseException ex) {
            throw new ProveedorFacturacionException("NubeFact no pudo descargar el recurso solicitado");
        } catch (RestClientException ex) {
            throw new ProveedorFacturacionException("No se pudo descargar el recurso desde NubeFact");
        }
    }

    private Map<String, Object> buildRequest(
            ElectronicInvoicingCommand command, Map<String, Object> snapshot) {
        BigDecimalValues totals = totals(snapshot);
        Map<String, Object> customer = map(snapshot.get("customer"));
        Map<String, Object> note = map(snapshot.get("note"));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operacion", "generar_comprobante");
        request.put("tipo_de_comprobante", typeCode(command.documentType()));
        request.put("serie", command.series());
        request.put("numero", command.documentNumber());
        request.put("sunat_transaction", "1");
        request.put("cliente_tipo_de_documento", customerDocumentType(text(customer.get("docType"), null)));
        request.put("cliente_numero_de_documento", text(customer.get("docNumber"), "-"));
        request.put("cliente_denominacion", text(customer.get("fullName"), "CLIENTE"));
        request.put("cliente_direccion", text(customer.get("address"), ""));
        request.put("cliente_email", text(customer.get("email"), ""));
        request.put("fecha_de_emision", LocalDate.now(ZoneId.of("America/Lima")).format(NUBEFACT_DATE));
        request.put("moneda", currencyCode(text(snapshot.get("currencyCode"), "PEN")));
        request.put("porcentaje_de_igv", totals.rate().multiply(java.math.BigDecimal.valueOf(100)));
        request.put("total_descuento", totals.discount());
        request.put("total_gravada", totals.taxable());
        request.put("total_inafecta", "");
        request.put("total_exonerada", totals.rate().signum() > 0 ? "" : totals.taxable());
        request.put("total_igv", totals.tax());
        request.put("total", totals.total());
        request.put("detraccion", "false");
        request.put("observaciones", text(snapshot.get("saleNumber"), ""));
        request.put("documento_que_se_modifica_tipo", text(note.get("sourceDocumentTypeCode"), ""));
        request.put("documento_que_se_modifica_serie", text(note.get("sourceSeries"), ""));
        request.put("documento_que_se_modifica_numero", text(note.get("sourceNumber"), ""));
        request.put("tipo_de_nota_de_credito", command.documentType() == ElectronicDocumentType.NOTA_CREDITO
                ? text(note.get("reasonCode"), "") : "");
        request.put("tipo_de_nota_de_debito", command.documentType() == ElectronicDocumentType.NOTA_DEBITO
                ? text(note.get("reasonCode"), "") : "");
        request.put("enviar_automaticamente_a_la_sunat", "true");
        request.put("enviar_automaticamente_al_cliente", "false");
        request.put("codigo_unico", text(snapshot.get("saleId"), ""));
        request.put("condiciones_de_pago", "CONTADO");
        request.put("medio_de_pago", "");
        request.put("formato_de_pdf", "");
        request.put("items", items(snapshot, totals.rate()));
        return request;
    }

    private List<Map<String, Object>> items(Map<String, Object> snapshot, java.math.BigDecimal rate) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> line : listOfMaps(snapshot.get("lines"))) {
            java.math.BigDecimal gross = decimal(line.get("subtotal"));
            java.math.BigDecimal value = rate.signum() > 0
                    ? gross.divide(java.math.BigDecimal.ONE.add(rate), 2, java.math.RoundingMode.HALF_UP) : gross;
            java.math.BigDecimal unitPrice = decimal(line.get("unitPrice"));
            java.math.BigDecimal unitValue = rate.signum() > 0
                    ? unitPrice.divide(java.math.BigDecimal.ONE.add(rate), 2, java.math.RoundingMode.HALF_UP) : unitPrice;
            java.math.BigDecimal igv = gross.subtract(value).setScale(2, java.math.RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("unidad_de_medida", "NIU");
            item.put("codigo", text(line.get("sku"), "ITEM"));
            item.put("descripcion", text(line.get("description"), "Producto"));
            item.put("cantidad", decimal(line.get("quantity")));
            item.put("valor_unitario", unitValue);
            item.put("precio_unitario", unitPrice);
            item.put("descuento", decimal(line.get("discountAmount")));
            item.put("subtotal", value);
            item.put("tipo_de_igv", rate.signum() > 0 ? "1" : "8");
            item.put("igv", igv);
            item.put("total", gross);
            item.put("anticipo_regularizacion", "false");
            item.put("anticipo_serie", "");
            item.put("anticipo_documento_numero", "");
            result.add(item);
        }
        return result;
    }

    private BigDecimalValues totals(Map<String, Object> snapshot) {
        java.math.BigDecimal rate = decimal(snapshot.get("igvRate"));
        java.math.BigDecimal total = decimal(snapshot.get("total"));
        java.math.BigDecimal taxable = rate.signum() > 0
                ? total.divide(java.math.BigDecimal.ONE.add(rate), 2, java.math.RoundingMode.HALF_UP) : total;
        return new BigDecimalValues(total, taxable, total.subtract(taxable).setScale(2, java.math.RoundingMode.HALF_UP),
                decimal(snapshot.get("discountAmount")), rate);
    }

    private Map<String, Object> queryRequest(DocumentReference reference) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operacion", "consultar_comprobante");
        request.put("tipo_de_comprobante", reference.typeCode());
        request.put("serie", reference.series());
        request.put("numero", reference.number());
        return request;
    }

    @SuppressWarnings("unchecked")
    private ElectronicInvoicingResult parseResult(Map<String, Object> body, int httpStatus,
            ElectronicInvoicingCommand command) {
        return parseResult(body, httpStatus,
                new DocumentReference(typeCode(command.documentType()), command.series(), command.documentNumber()));
    }

    @SuppressWarnings("unchecked")
    private ElectronicInvoicingResult parseResult(Map<String, Object> body, int httpStatus,
            DocumentReference reference) {
        Map<String, Object> invoice = invoice(body);
        String id = providerId(reference.typeCode(), reference.series(), reference.number());
        if (body != null && body.get("errors") != null) {
            return result(ElectronicDocumentStatus.REJECTED, id, reference, null,
                    "NUBEFACT_VALIDATION_ERROR", String.valueOf(body.get("errors")));
        }
        if (invoice.isEmpty()) {
            return result(httpStatus >= 400 ? ElectronicDocumentStatus.REJECTED : ElectronicDocumentStatus.ERROR,
                    id, reference, null, "NUBEFACT_EMPTY_RESPONSE", "NubeFact devolvió una respuesta sin comprobante");
        }
        Boolean accepted = booleanValue(invoice.get("aceptada_por_sunat"));
        String responseCode = text(invoice.get("sunat_responsecode"), null);
        String ticket = text(invoice.get("sunat_ticket_numero"), null);
        ElectronicDocumentStatus status = Boolean.TRUE.equals(accepted) || "0".equals(responseCode)
                ? ElectronicDocumentStatus.ACCEPTED
                : ticket != null && !ticket.isBlank() && (responseCode == null || responseCode.isBlank())
                        ? ElectronicDocumentStatus.PENDING : ElectronicDocumentStatus.REJECTED;
        return result(status, id, reference, invoice,
                responseCode, text(invoice.get("sunat_description"), text(invoice.get("sunat_soap_error"), null)));
    }

    private ElectronicInvoicingResult result(ElectronicDocumentStatus status, String id,
            DocumentReference reference, Map<String, Object> invoice, String code, String message) {
        return new ElectronicInvoicingResult(status, id, status.name(),
                text(invoice == null ? null : invoice.get("serie"), reference.series()),
                text(invoice == null ? null : invoice.get("numero"), reference.number()), code, message,
                text(invoice == null ? null : invoice.get("enlace_del_xml"), null),
                text(invoice == null ? null : invoice.get("enlace_del_cdr"), null));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invoice(Map<String, Object> body) {
        if (body == null || body.get("invoice") == null) return Map.of();
        Object value = body.get("invoice");
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        if (value instanceof String json) return parse(json);
        return Map.of();
    }

    private RestClient client(BillingConfigurationData configuration) {
        String token = credential(configuration, "token", "apiToken", "apiKey");
        if (token == null) throw new ProveedorFacturacionException("Falta el token de NubeFact");
        String url = configuration.apiUrl() == null ? "" : configuration.apiUrl().trim();
        if (url.isBlank() || !url.startsWith("https://")) {
            throw new ProveedorFacturacionException("La ruta de NubeFact debe ser una URL HTTPS completa");
        }
        if (configuration.environment() == BillingProviderEnvironment.PRODUCTION && !url.startsWith("https://")) {
            throw new ProveedorFacturacionException("La ruta de NubeFact en producción debe usar HTTPS");
        }
        return RestClient.builder().baseUrl(url.replaceAll("/+$", ""))
                .defaultHeader("Authorization", AUTHORIZATION_PREFIX + token)
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

    private DocumentReference parseProviderId(String value) {
        if (value == null) throw new ProveedorFacturacionException("NubeFact no tiene identificador de comprobante");
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new ProveedorFacturacionException("El identificador de NubeFact no es válido");
        }
        return new DocumentReference(parts[0], parts[1], parts[2]);
    }

    private String providerId(String typeCode, String series, String number) {
        return typeCode + "|" + series + "|" + number;
    }

    private String typeCode(ElectronicDocumentType type) {
        return switch (type) {
            case FACTURA -> "1";
            case BOLETA -> "2";
            case NOTA_CREDITO -> "3";
            case NOTA_DEBITO -> "4";
        };
    }

    private String currencyCode(String value) {
        return switch (value) {
            case "USD" -> "2";
            case "EUR" -> "3";
            default -> "1";
        };
    }

    private String customerDocumentType(String type) {
        return switch (type) {
            case "RUC" -> "6";
            case "DNI" -> "1";
            case "CE" -> "4";
            default -> "0";
        };
    }

    private byte[] unpack(byte[] content, String resource) {
        if (content.length < 2 || content[0] != 'P' || content[1] != 'K') return content;
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry = input.getNextEntry();
            if (entry == null) throw new ProveedorFacturacionException("El ZIP de NubeFact está vacío");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            input.transferTo(output);
            return output.toByteArray();
        } catch (java.io.IOException ex) {
            throw new ProveedorFacturacionException("No se pudo abrir el " + resource + " recibido de NubeFact");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new ProveedorFacturacionException("La respuesta JSON de NubeFact no es válida");
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

    private java.math.BigDecimal decimal(Object value) {
        if (value == null) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException ex) { return java.math.BigDecimal.ZERO; }
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private String stripDataPrefix(String value) {
        int comma = value.indexOf(',');
        return comma >= 0 ? value.substring(comma + 1) : value;
    }

    private record DocumentReference(String typeCode, String series, String number) { }

    private record BigDecimalValues(
            java.math.BigDecimal total, java.math.BigDecimal taxable, java.math.BigDecimal tax,
            java.math.BigDecimal discount, java.math.BigDecimal rate) { }
}
