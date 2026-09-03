package com.freestyleperu.aplicacion.facturacion;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.facturacion.service.ElectronicDocumentService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Concilia comprobantes que el proveedor dejó en proceso. Se ejecuta fuera de
 * una petición HTTP y fija explícitamente el tenant antes de consultar datos
 * protegidos por Hibernate {@code @TenantId}.
 */
@Component
public class ElectronicDocumentScheduler {

    private static final Logger log = LoggerFactory.getLogger(ElectronicDocumentScheduler.class);

    private final CompanySettingsRepository companySettingsRepository;
    private final ElectronicDocumentService electronicDocumentService;

    public ElectronicDocumentScheduler(
            CompanySettingsRepository companySettingsRepository,
            ElectronicDocumentService electronicDocumentService) {
        this.companySettingsRepository = companySettingsRepository;
        this.electronicDocumentService = electronicDocumentService;
    }

    @Scheduled(
            fixedDelayString = "${app.facturacion.status-poll-ms:60000}",
            initialDelayString = "${app.facturacion.status-initial-delay-ms:30000}")
    public void conciliarPendientes() {
        for (CompanySettings settings : companySettingsRepository.findAll()) {
            if (!settings.isElectronicInvoicingEnabled()) {
                continue;
            }

            TenantContext.set(settings.getId());
            try {
                List<Long> documentIds = electronicDocumentService.idsPendientesDeConciliacion();
                for (Long documentId : documentIds) {
                    try {
                        electronicDocumentService.actualizarEstado(documentId);
                    } catch (RuntimeException ex) {
                        // Un timeout o una caída temporal no invalida el CPE ni detiene
                        // la conciliación de los demás documentos/tenants.
                        log.warn("No se pudo conciliar el comprobante {} del tenant {}: {}",
                                documentId, settings.getId(), ex.getMessage());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo listar comprobantes pendientes del tenant {}: {}",
                        settings.getId(), ex.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}
