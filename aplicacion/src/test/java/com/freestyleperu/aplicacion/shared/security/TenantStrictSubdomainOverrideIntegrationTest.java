package com.freestyleperu.aplicacion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Prueba el escape hatch {@code app.tenant.strict-subdomain-resolution=false} descrito en el
 * Javadoc de {@link TenantResolutionFilter} — un despliegue que corre con el perfil "prod" (por
 * su tamaño de pool/hilos, ver application.yml) pero que TODAVÍA no tiene un subdominio real
 * configurado (ej. una demo servida por IP/localhost) necesita caer al tenant por defecto en vez
 * de un 404 duro. Activa los perfiles "prod"+"test" a la vez (test para usar H2, no MySQL real;
 * prod para que {@code environment.matchesProfiles("prod")} sea true, exactamente la condición
 * que el filtro evalúa) — así se prueba el flag real sin depender de una base de datos real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles({ "prod", "test" })
@TestPropertySource(properties = "app.tenant.strict-subdomain-resolution=false")
class TenantStrictSubdomainOverrideIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CompanySettingsRepository companySettingsRepository;

    @Test
    void conElFlagApagadoElPerfilProdCaeAlTenantPorDefectoEnVezDe404() {
        sembrarTenantPorDefecto();

        // TestRestTemplate pega a "localhost:<puerto>" sin ningún subdominio real — en "prod"
        // estricto esto sería 404 TENANT_NOT_FOUND (ver TenantResolutionIntegrationTest), pero
        // con el flag apagado debe comportarse como dev/test: cae al tenant por defecto.
        ResponseEntity<String> respuesta = restTemplate.getForEntity("/api/system/info", String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** Este test usa un contexto de Spring propio (perfiles "prod"+"test" distintos de "test"
     * solo), así que tiene su propia base H2 vacía — sin esto, la petición fallaría con 404 por
     * falta de tenant sembrado, no por el mecanismo que este test realmente quiere probar. */
    private void sembrarTenantPorDefecto() {
        CompanySettings settings = new CompanySettings();
        settings.setSlug("default");
        settings.setName("Freestyle Perú (semilla test)");
        settings.setCurrencyCode("PEN");
        settings.setCurrencySymbol("S/");
        settings.setIgvRate(new BigDecimal("0.18"));
        settings.setShippingFlatRate(new BigDecimal("15.00"));
        settings.setReservationDepositAmount(new BigDecimal("20.00"));
        settings.setReservationExpirationDays(3);
        settings.setPlan(Plan.ECOMMERCE);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setUpdatedAt(LocalDateTime.now());
        companySettingsRepository.save(settings);
    }
}
