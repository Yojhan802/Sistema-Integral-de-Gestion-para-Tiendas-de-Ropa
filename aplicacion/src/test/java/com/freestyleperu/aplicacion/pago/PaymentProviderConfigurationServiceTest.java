package com.freestyleperu.aplicacion.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderConfiguration;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderEnvironment;
import com.freestyleperu.aplicacion.pago.domain.PaymentProviderType;
import com.freestyleperu.aplicacion.pago.dto.request.ActualizarPaymentProviderRequest;
import com.freestyleperu.aplicacion.pago.repository.PaymentProviderConfigurationRepository;
import com.freestyleperu.aplicacion.pago.service.PaymentProviderConfigurationService;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.security.CredentialEncryptionService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PaymentProviderConfigurationServiceTest {

    private PaymentProviderConfigurationRepository repository;
    private CompanySettingsRepository companySettingsRepository;
    private PaymentProviderConfigurationService service;
    private CredentialEncryptionService encryption;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentProviderConfigurationRepository.class);
        companySettingsRepository = mock(CompanySettingsRepository.class);
        encryption = new CredentialEncryptionService("test-encryption-key");
        AuditService auditService = mock(AuditService.class);
        CompanySettings settings = new CompanySettings();
        settings.setOnlinePaymentsEnabled(true);
        when(companySettingsRepository.findById(TenantContext.DEFAULT_TENANT_ID)).thenReturn(Optional.of(settings));
        service = new PaymentProviderConfigurationService(
                repository, encryption, new ObjectMapper(), auditService, companySettingsRepository,
                List.of(provider(PaymentProviderType.NIUBIZ), provider(PaymentProviderType.CULQI), provider(PaymentProviderType.IZIPAY)));
        TenantContext.set(TenantContext.DEFAULT_TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void noPublicaIzipaySiFaltaHashKey() throws Exception {
        PaymentProviderConfiguration config = izipay(Map.of(
                "newPaymentButtonApiKey", "api-key"));
        when(repository.findAllByOrderByProviderAsc()).thenReturn(List.of(config));

        assertThat(service.listarPublicos()).isEmpty();
    }

    @Test
    void publicaCulqiCuandoTieneLlavePublicaYPrivada() throws Exception {
        PaymentProviderConfiguration config = new PaymentProviderConfiguration();
        config.setProvider(PaymentProviderType.CULQI);
        config.setEnabled(true);
        config.setPublicKey("pk_test_123");
        config.setCredentialsEncrypted(encryption.encrypt(new ObjectMapper().writeValueAsString(Map.of("secretKey", "sk_test_123"))));
        when(repository.findAllByOrderByProviderAsc()).thenReturn(List.of(config));

        assertThat(service.listarPublicos()).extracting(response -> response.provider())
                .containsExactly(PaymentProviderType.CULQI);
    }

    @Test
    void impideActivarNiubizIncompleto() {
        when(repository.findByProvider(PaymentProviderType.NIUBIZ)).thenReturn(Optional.empty());
        ActualizarPaymentProviderRequest request = new ActualizarPaymentProviderRequest(
                true, PaymentProviderEnvironment.TEST, null, "123456", null,
                Map.of("username", "integracion"));

        assertThatThrownBy(() -> service.actualizar(PaymentProviderType.NIUBIZ, request))
                .hasMessageContaining("Niubiz requiere");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private PaymentProviderConfiguration izipay(Map<String, String> credentials) throws Exception {
        PaymentProviderConfiguration config = new PaymentProviderConfiguration();
        config.setProvider(PaymentProviderType.IZIPAY);
        config.setEnabled(true);
        config.setMerchantCode("4000001");
        config.setPublicKey("rsa-public-key");
        config.setCredentialsEncrypted(encryption.encrypt(new ObjectMapper().writeValueAsString(credentials)));
        return config;
    }

    private com.freestyleperu.aplicacion.pago.domain.PaymentProvider provider(PaymentProviderType type) {
        return new com.freestyleperu.aplicacion.pago.domain.PaymentProvider() {
            @Override
            public PaymentProviderType type() {
                return type;
            }

            @Override
            public com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeResult charge(
                    com.freestyleperu.aplicacion.pago.port.PaymentProviderChargeCommand command,
                    com.freestyleperu.aplicacion.pago.port.PaymentProviderConfigurationData configuration) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
