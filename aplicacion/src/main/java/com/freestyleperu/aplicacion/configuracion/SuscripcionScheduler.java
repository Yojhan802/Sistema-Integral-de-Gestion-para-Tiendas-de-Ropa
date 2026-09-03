package com.freestyleperu.aplicacion.configuracion;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.time.LocalDate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revisión diaria del vencimiento de pago de cada negocio (multi-tenant, ver
 * docs/03-modelo-datos.md §15). Si un tenant pasa su {@code nextPaymentDue}
 * más el margen de gracia, se suspende solo — SubscriptionStatusFilter
 * bloquea entonces todo el sistema para ESE tenant hasta que el operador de
 * la plataforma lo reactive tras recibir el pago.
 *
 * <p>No corre dentro de una petición HTTP (es un job {@code @Scheduled}), así
 * que nada fija {@link TenantContext} de antemano — {@code findAll()} en sí
 * es seguro sin contexto porque {@code CompanySettings} ES la tabla de
 * tenants (no tiene {@code @TenantId}), pero cada llamada a
 * {@code auditService.log(...)} SÍ necesita el tenant correcto (AuditLog sí
 * lo tiene) — se fija explícitamente alrededor de esa única línea, por tenant.
 */
@Component
public class SuscripcionScheduler {

    /**
     * Días de cortesía después de que se acaba el mes pagado. Va en cero porque el cobro
     * es anticipado: pasada la fecha, el servicio ya no está pagado, y regalar días sería
     * dar servicio gratis. El aviso de vencimiento del panel avisa con antelación, así que
     * nadie se queda fuera por sorpresa.
     */
    private static final int DIAS_GRACIA = 0;

    private final CompanySettingsRepository companySettingsRepository;
    private final AuditService auditService;

    public SuscripcionScheduler(CompanySettingsRepository companySettingsRepository, AuditService auditService) {
        this.companySettingsRepository = companySettingsRepository;
        this.auditService = auditService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void revisarVencimiento() {
        companySettingsRepository.findAll().forEach(settings -> {
            if (debeSuspenderse(settings)) {
                settings.setSubscriptionStatus(SubscriptionStatus.SUSPENDIDA);
                TenantContext.set(settings.getId());
                try {
                    auditService.log("SUSCRIPCION_SUSPENDIDA_AUTOMATICAMENTE", "COMPANY_SETTINGS", settings.getId(),
                            null, settings.getNextPaymentDue(), AuditResult.SUCCESS);
                } finally {
                    TenantContext.clear();
                }
            }
        });
    }

    /**
     * {@code nextPaymentDue} es el primer día NO cubierto: un periodo del 3 de setiembre al
     * 3 de octubre cubre hasta el 2. Por eso se suspende al llegar la fecha, no al pasarla.
     */
    private boolean debeSuspenderse(CompanySettings settings) {
        return settings.getSubscriptionStatus() == SubscriptionStatus.ACTIVA
                && settings.getNextPaymentDue() != null
                && !LocalDate.now().isBefore(settings.getNextPaymentDue().plusDays(DIAS_GRACIA));
    }
}
