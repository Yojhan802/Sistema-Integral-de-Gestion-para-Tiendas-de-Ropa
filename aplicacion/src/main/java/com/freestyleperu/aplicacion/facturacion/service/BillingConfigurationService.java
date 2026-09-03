package com.freestyleperu.aplicacion.facturacion.service;

import com.freestyleperu.aplicacion.facturacion.domain.BillingConfiguration;
import com.freestyleperu.aplicacion.facturacion.domain.BillingProvider;
import com.freestyleperu.aplicacion.facturacion.domain.BillingProviderEnvironment;
import com.freestyleperu.aplicacion.facturacion.dto.request.ActualizarBillingConfigurationRequest;
import com.freestyleperu.aplicacion.facturacion.dto.response.BillingConfigurationResponse;
import com.freestyleperu.aplicacion.facturacion.repository.BillingConfigurationRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.security.CredentialEncryptionService;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class BillingConfigurationService {

    private final BillingConfigurationRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final StoreCatalogSyncService storeCatalogSyncService;

    public BillingConfigurationService(
            BillingConfigurationRepository repository,
            CredentialEncryptionService encryptionService,
            ObjectMapper objectMapper,
            AuditService auditService,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public BillingConfigurationResponse obtener() {
        return repository.findFirstByOrderByIdAsc()
                .map(this::toResponse)
                .orElseGet(() -> new BillingConfigurationResponse(
                        BillingProvider.VERIFACT, false, BillingProviderEnvironment.TEST, null,
                        null, null, null, null, false, List.of()));
    }

    @Transactional
    public BillingConfigurationResponse actualizar(ActualizarBillingConfigurationRequest request) {
        BillingConfiguration config = repository.findFirstByOrderByIdAsc().orElseGet(BillingConfiguration::new);
        Map<String, String> credentials = request.credentials() == null ? Collections.emptyMap() : request.credentials();
        if (request.enabled() && credentials.isEmpty() && !isConfigured(config)) {
            throw new OperacionNoPermitidaException(
                    "No puedes activar la facturación electrónica sin guardar primero sus credenciales");
        }

        if (request.provider() != null) {
            config.setProvider(request.provider());
        }
        config.setEnabled(request.enabled());
        config.setEnvironment(request.environment());
        config.setApiUrl(blankToNull(request.apiUrl()));
        if (request.invoiceSeries() != null) {
            config.setInvoiceSeries(blankToNull(request.invoiceSeries()));
        }
        if (request.receiptSeries() != null) {
            config.setReceiptSeries(blankToNull(request.receiptSeries()));
        }
        if (request.creditNoteSeries() != null) {
            config.setCreditNoteSeries(blankToNull(request.creditNoteSeries()));
        }
        if (request.debitNoteSeries() != null) {
            config.setDebitNoteSeries(blankToNull(request.debitNoteSeries()));
        }
        if (!credentials.isEmpty()) {
            try {
                config.setCredentialsEncrypted(encryptionService.encrypt(
                        objectMapper.writeValueAsString(mergeCredentials(config, credentials))));
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudieron guardar las credenciales de facturación", ex);
            }
        }
        BillingConfiguration saved = repository.save(config);
        auditService.log("CONFIGURACION_FACTURACION_ACTUALIZADA", "BILLING_CONFIGURATION", saved.getId(), null,
                Map.of("provider", saved.getProvider().name(), "enabled", saved.isEnabled(),
                        "environment", saved.getEnvironment().name()), AuditResult.SUCCESS);
        storeCatalogSyncService.requestRefresh();
        return toResponse(saved);
    }

    private BillingConfigurationResponse toResponse(BillingConfiguration config) {
        return new BillingConfigurationResponse(config.getProvider(), config.isEnabled(), config.getEnvironment(),
                config.getApiUrl(), config.getInvoiceSeries(), config.getReceiptSeries(),
                config.getCreditNoteSeries(), config.getDebitNoteSeries(), isConfigured(config), credentialKeys(config));
    }

    private boolean isConfigured(BillingConfiguration config) {
        return config.getCredentialsEncrypted() != null && !config.getCredentialsEncrypted().isBlank();
    }

    private List<String> credentialKeys(BillingConfiguration config) {
        if (!isConfigured(config)) {
            return List.of();
        }
        try {
            String json = encryptionService.decrypt(config.getCredentialsEncrypted());
            Map<String, String> values = objectMapper.readValue(json, Map.class);
            return values.keySet().stream().sorted().toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, String> mergeCredentials(
            BillingConfiguration config, Map<String, String> submitted) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (isConfigured(config)) {
            try {
                String json = encryptionService.decrypt(config.getCredentialsEncrypted());
                merged.putAll(objectMapper.readValue(json, Map.class));
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudieron leer las credenciales existentes", ex);
            }
        }
        submitted.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                merged.put(key.trim(), value);
            }
        });
        return merged;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
