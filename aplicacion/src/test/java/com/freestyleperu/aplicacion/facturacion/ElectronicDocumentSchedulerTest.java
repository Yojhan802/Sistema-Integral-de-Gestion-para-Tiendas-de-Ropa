package com.freestyleperu.aplicacion.facturacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.facturacion.service.ElectronicDocumentService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ElectronicDocumentSchedulerTest {

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    @Test
    void procesaSoloTenantsConFacturacionYContinuaSiUnDocumentoFalla() {
        CompanySettings deshabilitada = company(10L, false);
        CompanySettings habilitada = company(20L, true);
        CompanySettingsRepository settings = mock(CompanySettingsRepository.class);
        ElectronicDocumentService service = mock(ElectronicDocumentService.class);
        when(settings.findAll()).thenReturn(List.of(deshabilitada, habilitada));
        when(service.idsPendientesDeConciliacion()).thenReturn(List.of(7L, 8L));
        doThrow(new RuntimeException("timeout simulado")).when(service).actualizarEstado(7L);

        new ElectronicDocumentScheduler(settings, service).conciliarPendientes();

        verify(service).idsPendientesDeConciliacion();
        verify(service).actualizarEstado(7L);
        verify(service).actualizarEstado(8L);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void noConsultaDocumentosDeUnTenantConFacturacionDesactivada() {
        CompanySettingsRepository settings = mock(CompanySettingsRepository.class);
        ElectronicDocumentService service = mock(ElectronicDocumentService.class);
        when(settings.findAll()).thenReturn(List.of(company(10L, false)));

        new ElectronicDocumentScheduler(settings, service).conciliarPendientes();

        verifyNoInteractions(service);
        assertThat(TenantContext.get()).isNull();
    }

    private CompanySettings company(Long id, boolean electronicInvoicingEnabled) {
        CompanySettings settings = new CompanySettings();
        settings.setId(id);
        settings.setElectronicInvoicingEnabled(electronicInvoicingEnabled);
        return settings;
    }
}
