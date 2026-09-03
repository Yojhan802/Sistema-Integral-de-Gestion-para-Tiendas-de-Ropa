package com.freestyleperu.aplicacion.plataforma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.plataforma.dto.request.RegistrarPagoRequest;
import com.freestyleperu.aplicacion.plataforma.dto.response.PagoSuscripcionResponse;
import com.freestyleperu.aplicacion.plataforma.dto.response.RenovacionResponse;
import com.freestyleperu.aplicacion.plataforma.service.SubscriptionRenewalService;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renovar mueve el vencimiento y deja constancia del cobro.
 *
 * <p>El cobro es anticipado, así que la regla que se fija aquí es {@code max(vencimiento,
 * hoy)}: pagar por adelantado no hace perder días ya pagados, y volver tras estar fuera
 * no cobra el tiempo en que la empresa no tuvo el sistema. No hay mora que acumular.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RenovacionSuscripcionIntegrationTest {

    private static final Long TENANT = 1L;

    @Autowired private SubscriptionRenewalService renewalService;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @PersistenceContext private EntityManager entityManager;

    /** Ver el comentario equivalente en ModulosPorEmpresaIntegrationTest: H2 y el id 1. */
    private CompanySettings aseguraEmpresa(LocalDate vencimiento, SubscriptionStatus estado) {
        CompanySettings settings = companySettingsRepository.findById(TENANT).orElse(null);
        if (settings == null) {
            settings = new CompanySettings();
            settings.setSlug("default");
            settings.setName("Empresa de prueba");
            settings.setCurrencyCode("PEN");
            settings.setCurrencySymbol("S/");
            settings.setIgvRate(new BigDecimal("0.18"));
            settings.setShippingFlatRate(new BigDecimal("15.00"));
            settings.setReservationDepositAmount(new BigDecimal("20.00"));
            settings.setReservationExpirationDays(3);
            settings.setPlan(Plan.STARTER);
            settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
            settings.setUpdatedAt(LocalDateTime.now());
            Long generado = companySettingsRepository.saveAndFlush(settings).getId();
            if (!TENANT.equals(generado)) {
                entityManager.createNativeQuery("UPDATE company_settings SET id = 1 WHERE id = :id")
                        .setParameter("id", generado).executeUpdate();
                entityManager.clear();
            }
            settings = companySettingsRepository.findById(TENANT).orElseThrow();
        }
        settings.setNextPaymentDue(vencimiento);
        settings.setSubscriptionStatus(estado);
        settings.setUpdatedAt(LocalDateTime.now());
        return companySettingsRepository.saveAndFlush(settings);
    }

    private static RegistrarPagoRequest pago(String monto, int meses) {
        return new RegistrarPagoRequest(new BigDecimal(monto), "TRANSFERENCIA", "OP-1001", meses, "Pago mensual");
    }

    /** Paga antes de que se le acabe el mes: no puede perder los días que ya tenía. */
    @Test
    void pagarPorAdelantadoNoHacePerderLosDiasYaPagados() {
        LocalDate vencimiento = LocalDate.now().plusDays(6);
        aseguraEmpresa(vencimiento, SubscriptionStatus.ACTIVA);

        RenovacionResponse resultado = renewalService.renovar(TENANT, pago("152.00", 1), 5L, "operador");

        assertThat(resultado.pago().periodoInicio()).isEqualTo(vencimiento);
        assertThat(resultado.nextPaymentDue()).isEqualTo(vencimiento.plusMonths(1));
    }

    /**
     * El caso del cliente que pausa porque no lo va a usar y vuelve meses después. Como el
     * cobro es anticipado, durante ese tiempo no tuvo el sistema: cobrarle el hueco sería
     * cobrarle por no usarlo.
     */
    @Test
    void volverDespuesDeMesesFueraCuestaUnSoloMesContadoDesdeHoy() {
        aseguraEmpresa(LocalDate.now().minusMonths(3), SubscriptionStatus.SUSPENDIDA);

        RenovacionResponse resultado = renewalService.renovar(TENANT, pago("152.00", 1), 5L, "operador");

        assertThat(resultado.pago().periodoInicio()).isEqualTo(LocalDate.now());
        assertThat(resultado.nextPaymentDue()).isEqualTo(LocalDate.now().plusMonths(1));
        // Un solo mes basta para volver, sin importar cuánto estuvo fuera.
        assertThat(resultado.reactivada()).isTrue();
        assertThat(resultado.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVA);
    }

    @Test
    void cobrarAUnaEmpresaSuspendidaLaReactivaSola() {
        aseguraEmpresa(LocalDate.now().minusDays(20), SubscriptionStatus.SUSPENDIDA);

        RenovacionResponse resultado = renewalService.renovar(TENANT, pago("152.00", 1), 5L, "operador");

        // Cobrar y tener que acordarse de reactivar a mano era el paso que se olvidaba.
        assertThat(resultado.reactivada()).isTrue();
        assertThat(resultado.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVA);
        assertThat(companySettingsRepository.findById(TENANT).orElseThrow().getSubscriptionStatus())
                .isEqualTo(SubscriptionStatus.ACTIVA);
    }

    /** Comprar varios meses de golpe simplemente extiende el periodo desde hoy. */
    @Test
    void pagarVariosMesesPorAdelantadoExtiendeElPeriodoCompleto() {
        aseguraEmpresa(LocalDate.now().minusMonths(3), SubscriptionStatus.SUSPENDIDA);

        RenovacionResponse resultado = renewalService.renovar(TENANT, pago("456.00", 3), 5L, "operador");

        assertThat(resultado.nextPaymentDue()).isEqualTo(LocalDate.now().plusMonths(3));
        assertThat(resultado.reactivada()).isTrue();
        assertThat(resultado.pago().monto()).isEqualByComparingTo("456.00");
    }

    @Test
    void cadaCobroQuedaEnElHistorialConSuAutorYSuPeriodo() {
        aseguraEmpresa(LocalDate.now(), SubscriptionStatus.ACTIVA);
        renewalService.renovar(TENANT, pago("152.00", 1), 5L, "operador.uno");
        renewalService.renovar(TENANT, pago("152.00", 1), 9L, "operador.dos");

        List<PagoSuscripcionResponse> historial = renewalService.historial(TENANT);

        assertThat(historial).hasSize(2);
        // Dos cobros seguidos encadenan: el segundo arranca donde termina el primero,
        // que ya está en el futuro.
        assertThat(historial.get(0).periodoInicio()).isEqualTo(historial.get(1).periodoFin());
        assertThat(historial).allSatisfy(cobro -> {
            assertThat(cobro.metodo()).isEqualTo("TRANSFERENCIA");
            assertThat(cobro.referencia()).isEqualTo("OP-1001");
            assertThat(cobro.origen()).isEqualTo("MANUAL");
        });
        assertThat(historial).extracting(PagoSuscripcionResponse::registradoPor)
                .containsExactlyInAnyOrder("operador.uno", "operador.dos");
    }

    @Test
    void unaEmpresaSinVencimientoPrevioArrancaElPeriodoHoy() {
        aseguraEmpresa(null, SubscriptionStatus.ACTIVA);

        RenovacionResponse resultado = renewalService.renovar(TENANT, pago("75.00", 1), 5L, "operador");

        assertThat(resultado.pago().periodoInicio()).isEqualTo(LocalDate.now());
        assertThat(resultado.nextPaymentDue()).isEqualTo(LocalDate.now().plusMonths(1));
    }

    @Test
    void renovarUnaEmpresaInexistenteFalla() {
        assertThatThrownBy(() -> renewalService.renovar(999999L, pago("10.00", 1), 5L, "operador"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
