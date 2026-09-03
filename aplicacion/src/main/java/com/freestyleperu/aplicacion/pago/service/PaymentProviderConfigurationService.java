package com.freestyleperu.aplicacion.pago.service;

import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.domain.PaymentProvider;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderConfiguration;
import com.freestyleperu.aplicacion.pago.dto.request.ActualizarPaymentProviderRequest;
import com.freestyleperu.aplicacion.pago.dto.response.PaymentProviderResponse;
import com.freestyleperu.aplicacion.pago.dto.response.PublicPaymentProviderResponse;
import com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData;
import com.freestyleperu.aplicacion.pago.repository.PaymentProviderConfigurationRepository;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.security.CredentialEncryptionService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import com.freestyleperu.aplicacion.tienda.service.StoreCatalogSyncService;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class PaymentProviderConfigurationService {

    private final PaymentProviderConfigurationRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final CompanySettingsRepository companySettingsRepository;
    private final List<PaymentProvider> providers;
    private final StoreCatalogSyncService storeCatalogSyncService;

    /** Constructor legado para pruebas/extensiones que aún no conocen la sincronización pública. */
    public PaymentProviderConfigurationService(
            PaymentProviderConfigurationRepository repository,
            CredentialEncryptionService encryptionService,
            ObjectMapper objectMapper,
            AuditService auditService,
            CompanySettingsRepository companySettingsRepository,
            List<PaymentProvider> providers) {
        this(repository, encryptionService, objectMapper, auditService, companySettingsRepository, providers, null);
    }

    @Autowired
    public PaymentProviderConfigurationService(
            PaymentProviderConfigurationRepository repository,
            CredentialEncryptionService encryptionService,
            ObjectMapper objectMapper,
            AuditService auditService,
            CompanySettingsRepository companySettingsRepository,
            List<PaymentProvider> providers,
            StoreCatalogSyncService storeCatalogSyncService) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.companySettingsRepository = companySettingsRepository;
        this.providers = providers;
        this.storeCatalogSyncService = storeCatalogSyncService;
    }

    public List<PaymentProviderResponse> listar() {
        Map<PaymentProviderType, PaymentProviderConfiguration> existentes = new LinkedHashMap<>();
        repository.findAllByOrderByProviderAsc().forEach(config -> existentes.put(config.getProvider(), config));
        return Arrays.stream(PaymentProviderType.values())
                .map(provider -> existentes.containsKey(provider)
                        ? toResponse(existentes.get(provider))
                        : new PaymentProviderResponse(provider, false, com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment.TEST,
                                null, null, null, false, List.of()))
                .toList();
    }

    /** Configuración estrictamente pública que el checkout puede usar para cargar el SDK del proveedor. */
    public List<PublicPaymentProviderResponse> listarPublicos() {
        boolean onlinePaymentsEnabled = companySettingsRepository.findById(TenantContext.getOrDefault())
                .map(settings -> settings.isOnlinePaymentsEnabled())
                .orElse(false);
        if (!onlinePaymentsEnabled) {
            return List.of();
        }
        return repository.findAllByOrderByProviderAsc().stream()
                .filter(config -> config.isEnabled() && configurationReady(config) && tieneAdaptador(config.getProvider()))
                .map(config -> new PublicPaymentProviderResponse(config.getProvider(), config.getEnvironment(),
                        config.getApiUrl(), config.getMerchantCode(), config.getPublicKey()))
                .toList();
    }

    /** Indica si un proveedor puede recibir un intento de cobro en el tenant actual. */
    public boolean estaDisponible(PaymentProviderType provider) {
        return listarPublicos().stream().anyMatch(publicProvider -> publicProvider.provider() == provider);
    }

    /**
     * Resuelve credenciales únicamente para código interno del backend. Los
     * controladores nunca deben invocar este método ni serializar su resultado.
     */
    public Optional<PaymentProviderConfigurationData> obtenerParaBackend(PaymentProviderType provider) {
        return repository.findByProvider(provider)
                .filter(config -> config.isEnabled() && configurationReady(config))
                .map(config -> {
                    try {
                        Map<String, String> credentials = objectMapper.readValue(
                                encryptionService.decrypt(config.getCredentialsEncrypted()), Map.class);
                        return new PaymentProviderConfigurationData(
                                config.getProvider(), config.getEnvironment(), config.getApiUrl(),
                                config.getMerchantCode(), config.getPublicKey(), credentials);
                    } catch (Exception ex) {
                        throw new IllegalStateException("No se pudieron leer las credenciales de " + provider, ex);
                    }
                });
    }

    @Transactional
    @CacheEvict(cacheNames = "storeCatalogPaymentMethods", keyGenerator = "tenantAwareKeyGenerator")
    public PaymentProviderResponse actualizar(PaymentProviderType provider, ActualizarPaymentProviderRequest request) {
        PaymentProviderConfiguration config = repository.findByProvider(provider).orElseGet(() -> {
            PaymentProviderConfiguration nuevo = new PaymentProviderConfiguration();
            nuevo.setProvider(provider);
            return nuevo;
        });

        Map<String, String> credentials = request.credentials() == null ? Collections.emptyMap() : request.credentials();
        Map<String, String> effectiveCredentials = credentials.isEmpty()
                ? existingCredentials(config)
                : mergeCredentials(config, credentials);

        config.setEnabled(request.enabled());
        config.setEnvironment(request.environment());
        config.setApiUrl(blankToNull(request.apiUrl()));
        config.setMerchantCode(blankToNull(request.merchantCode()));
        config.setPublicKey(blankToNull(request.publicKey()));
        if (request.enabled() && !configurationReady(provider, config.getMerchantCode(), config.getPublicKey(), effectiveCredentials)) {
            throw new OperacionNoPermitidaException(missingConfigurationMessage(provider));
        }
        if (!credentials.isEmpty()) {
            try {
                config.setCredentialsEncrypted(encryptionService.encrypt(
                        objectMapper.writeValueAsString(effectiveCredentials)));
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudieron guardar las credenciales de " + provider, ex);
            }
        }
        PaymentProviderConfiguration saved = repository.save(config);
        auditService.log("CONFIGURACION_PASARELA_ACTUALIZADA", "PAYMENT_PROVIDER_CONFIGURATION", saved.getId(), null,
                Map.of("provider", provider.name(), "enabled", saved.isEnabled(), "environment", saved.getEnvironment().name()),
                AuditResult.SUCCESS);
        if (storeCatalogSyncService != null) {
            storeCatalogSyncService.requestRefresh();
        }
        return toResponse(saved);
    }

    private PaymentProviderResponse toResponse(PaymentProviderConfiguration config) {
        return new PaymentProviderResponse(config.getProvider(), config.isEnabled(), config.getEnvironment(),
                config.getApiUrl(), config.getMerchantCode(), config.getPublicKey(), isConfigured(config), credentialKeys(config));
    }

    private boolean isConfigured(PaymentProviderConfiguration config) {
        return config.getCredentialsEncrypted() != null && !config.getCredentialsEncrypted().isBlank();
    }

    private boolean configurationReady(PaymentProviderConfiguration config) {
        return configurationReady(config.getProvider(), config.getMerchantCode(), config.getPublicKey(), existingCredentials(config));
    }

    private boolean configurationReady(
            PaymentProviderType provider, String merchantCode, String publicKey, Map<String, String> credentials) {
        return switch (provider) {
            case NIUBIZ -> present(merchantCode)
                    && hasCredential(credentials, "username", "user", "integrationUser")
                    && hasCredential(credentials, "password", "integrationPassword", "secret");
            case CULQI -> present(publicKey)
                    && hasCredential(credentials, "secretKey", "privateKey", "apiKey");
            case IZIPAY -> present(merchantCode)
                    && present(publicKey)
                    && hasCredential(credentials, "newPaymentButtonApiKey", "paymentButtonApiKey", "apiKey", "sessionApiKey")
                    && hasCredential(credentials, "hashKey", "keyHash");
        };
    }

    private String missingConfigurationMessage(PaymentProviderType provider) {
        return switch (provider) {
            case NIUBIZ -> "Niubiz requiere código de comercio, username y password antes de activarse";
            case CULQI -> "Culqi requiere llave pública y llave privada antes de activarse";
            case IZIPAY -> "Izipay requiere código de comercio, clave pública RSA, API key y hashKey antes de activarse";
        };
    }

    private boolean hasCredential(Map<String, String> credentials, String... keys) {
        return java.util.Arrays.stream(keys).anyMatch(key -> present(credentials.get(key)));
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> existingCredentials(PaymentProviderConfiguration config) {
        if (!isConfigured(config)) return Collections.emptyMap();
        try {
            return objectMapper.readValue(encryptionService.decrypt(config.getCredentialsEncrypted()), Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudieron leer las credenciales existentes", ex);
        }
    }

    private boolean tieneAdaptador(PaymentProviderType provider) {
        return providers.stream().anyMatch(candidate -> candidate.type() == provider);
    }

    private List<String> credentialKeys(PaymentProviderConfiguration config) {
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
            PaymentProviderConfiguration config, Map<String, String> submitted) {
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
