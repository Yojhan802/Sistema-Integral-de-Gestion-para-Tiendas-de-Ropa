package com.freestyleperu.aplicacion.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ver docs/03-modelo-datos.md §15 y RN-23 — suspensión automática por falta de pago.
 *
 * <p>{@code SuscripcionScheduler} ya itera todos los tenants ({@code findAll()}, ver Fase 2 del
 * plan de multi-tenant), pero estas pruebas solo ejercitan el tenant en id=1 — es el único que
 * existe en el H2 compartido del suite. Otras clases de test (p. ej.
 * {@code IdentidadEmpresaIntegrationTest}, sin {@code @Transactional}, corre contra un servidor
 * real) ya siembran y dejan permanentemente en ese H2 una fila "tenant por defecto" en id=1. Por
 * eso {@code sembrar} reutiliza esa fila si ya existe (actualizando sus campos) en vez de
 * insertar una nueva — así funciona tanto en aislamiento (donde nadie sembró id=1 todavía y el
 * primer insert lo obtiene naturalmente) como dentro del suite completo (donde ya existe). El
 * rollback de {@code @Transactional} al final de cada test deshace los cambios igual en ambos casos.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SuscripcionSchedulerIntegrationTest {

    @Autowired private SuscripcionScheduler suscripcionScheduler;
    @Autowired private CompanySettingsRepository companySettingsRepository;

    /** El cobro es anticipado: pasada la fecha, el servicio ya no está pagado. */
    @Test
    void suspendeCuandoSeAcabaElMesPagado() {
        Long id = sembrar(LocalDate.now().minusDays(10));
        suscripcionScheduler.revisarVencimiento();
        assertThat(companySettingsRepository.findById(id).orElseThrow().getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.SUSPENDIDA);
    }

    /**
     * Sin días de cortesía: {@code nextPaymentDue} es el primer día no cubierto, así que
     * ese mismo día ya no hay servicio pagado.
     */
    @Test
    void suspendeElMismoDiaDelVencimientoSinRegalarDias() {
        Long id = sembrar(LocalDate.now());
        suscripcionScheduler.revisarVencimiento();
        assertThat(companySettingsRepository.findById(id).orElseThrow().getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.SUSPENDIDA);
    }

    @Test
    void noSuspendeMientrasElMesSigaPagado() {
        Long id = sembrar(LocalDate.now().plusDays(1));
        suscripcionScheduler.revisarVencimiento();
        assertThat(companySettingsRepository.findById(id).orElseThrow().getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.ACTIVA);
    }

    @Test
    void noSuspendeSiNoHayFechaDePagoConfigurada() {
        Long id = sembrar(null);
        suscripcionScheduler.revisarVencimiento();
        assertThat(companySettingsRepository.findById(id).orElseThrow().getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.ACTIVA);
    }

    /** Reutiliza la fila tenant en id=1 si ya existe, o la crea; devuelve su id (ver Javadoc). */
    private Long sembrar(LocalDate nextPaymentDue) {
        CompanySettings settings = companySettingsRepository.findById(1L).orElseGet(CompanySettings::new);
        if (settings.getId() == null) {
            settings.setSlug("default-" + System.nanoTime());
            settings.setName("Freestyle Perú (semilla test)");
        }
        settings.setCurrencyCode("PEN");
        settings.setCurrencySymbol("S/");
        settings.setIgvRate(new BigDecimal("0.18"));
        settings.setShippingFlatRate(new BigDecimal("15.00"));
        settings.setReservationDepositAmount(new BigDecimal("20.00"));
        settings.setReservationExpirationDays(3);
        settings.setPlan(Plan.ECOMMERCE);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setNextPaymentDue(nextPaymentDue);
        settings.setUpdatedAt(LocalDateTime.now());
        return companySettingsRepository.save(settings).getId();
    }
}
