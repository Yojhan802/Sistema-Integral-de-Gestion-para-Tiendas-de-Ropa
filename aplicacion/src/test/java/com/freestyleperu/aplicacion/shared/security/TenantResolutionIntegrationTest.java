package com.freestyleperu.aplicacion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.auth.dto.LoginRequest;
import com.freestyleperu.aplicacion.auth.dto.LoginResponse;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Prueba de la Fase 2 del plan de multi-tenant (resolución de tenant por subdominio, JWT con
 * {@code tenantId}, guardia cruzada en JwtAuthenticationFilter) contra un servidor HTTP real —
 * mismo enfoque que SeguridadIntegrationTest/PlanGateIntegrationTest. Como no hay DNS comodín
 * real disponible en pruebas, usa el header de desarrollo {@code X-Tenant-Slug} que
 * TenantResolutionFilter admite fuera del perfil "prod" (ver su Javadoc) para simular el
 * subdominio de cada negocio.
 *
 * <p>Deliberadamente sin {@code @Transactional} a nivel de clase (misma razón que
 * VerticalNoRopaIntegrationTest: el resolver de tenant de Hibernate se consulta al ABRIR una
 * sesión, no en cada query) — cada guardado directo por repositorio abre su propia transacción en
 * el momento en que {@link TenantContext} ya está fijado. La limpieza usa SQL nativo en su propia
 * transacción explícita, en orden seguro de FKs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class TenantResolutionIntegrationTest {

    private static final String HEADER_TENANT_SLUG = "X-Tenant-Slug";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long tenantSegundoId;

    @AfterEach
    void limpiar() {
        if (tenantSegundoId != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                entityManager.createNativeQuery("DELETE FROM refresh_tokens WHERE tenant_id = :t")
                        .setParameter("t", tenantSegundoId).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM users WHERE tenant_id = :t")
                        .setParameter("t", tenantSegundoId).executeUpdate();
                entityManager.createNativeQuery("DELETE FROM company_settings WHERE id = :t")
                        .setParameter("t", tenantSegundoId).executeUpdate();
            });
            tenantSegundoId = null;
        }
    }

    @Test
    void unSlugDesconocidoDevuelve404() {
        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/api/system/info", HttpMethod.GET,
                new HttpEntity<>(headersConSlug("no-existe-" + System.nanoTime())), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).contains("TENANT_NOT_FOUND");
    }

    @Test
    void unTokenDeUnTenantEsRechazadoContraOtroTenantYAceptadoContraElSuyo() {
        // Garantiza que el tenant 1 (el default al que cae TenantContext sin header) exista de
        // antemano — si este test corre aislado (sin el resto del suite, que normalmente ya lo
        // sembró), el primer INSERT en una tabla vacía tomaría id=1 por auto-incremento y el
        // "otro" tenant dejaría de ser realmente otro, invalidando la prueba.
        asegurarTenantUno();

        String slugSegundo = "segundo-negocio-" + System.nanoTime();
        tenantSegundoId = crearTenantSegundo(slugSegundo);
        assertThat(tenantSegundoId).isNotEqualTo(TenantContext.DEFAULT_TENANT_ID);
        crearUsuarioEnTenant(tenantSegundoId, "tenant2.staff");

        String tokenTenant2 = login(slugSegundo, "tenant2.staff", "ClaveValida123");

        // El token del tenant 2, presentado bajo el subdominio correcto, funciona.
        ResponseEntity<String> conSuTenant = get("/api/auth/me", tokenTenant2, slugSegundo);
        assertThat(conSuTenant.getStatusCode()).isEqualTo(HttpStatus.OK);

        // El mismo token, presentado sin header (resuelve al tenant 1 por defecto), se rechaza —
        // JwtAuthenticationFilter detecta el cruce tenant-del-token vs. tenant-resuelto y no autentica.
        ResponseEntity<String> sinHeader = get("/api/auth/me", tokenTenant2, null);
        assertThat(sinHeader.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void laRutaDeOpsFuncionaSinImportarElTenantPorqueVieneExplicitoEnElBody() {
        tenantSegundoId = crearTenantSegundo("ops-tenant-" + System.nanoTime());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Ops-Key", "test-ops-key-1234");
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", tenantSegundoId);
        body.put("subscriptionStatus", "SUSPENDIDA");
        body.put("nextPaymentDue", null);

        ResponseEntity<String> respuesta = restTemplate.exchange(
                "/api/system/subscription", HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);

        CompanySettings actualizado = companySettingsRepository.findById(tenantSegundoId).orElseThrow();
        assertThat(actualizado.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.SUSPENDIDA);
    }

    private String login(String slug, String username, String password) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(username, password), headersConSlug(slug)), LoginResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().accessToken();
    }

    private ResponseEntity<String> get(String path, String token, String slug) {
        HttpHeaders headers = headersConSlug(slug);
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private HttpHeaders headersConSlug(String slug) {
        HttpHeaders headers = new HttpHeaders();
        if (slug != null) {
            headers.set(HEADER_TENANT_SLUG, slug);
        }
        return headers;
    }

    /** Ver Javadoc del test que la llama: siembra el tenant 1 si nadie más del suite ya lo hizo. */
    private void asegurarTenantUno() {
        companySettingsRepository.findById(TenantContext.DEFAULT_TENANT_ID).orElseGet(() -> {
            CompanySettings settings = new CompanySettings();
            settings.setSlug("default-" + System.nanoTime());
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
            return companySettingsRepository.save(settings);
        });
    }

    private Long crearTenantSegundo(String slug) {
        CompanySettings settings = new CompanySettings();
        settings.setSlug(slug);
        settings.setName("Segundo Negocio Demo");
        settings.setCurrencyCode("PEN");
        settings.setCurrencySymbol("S/");
        settings.setIgvRate(new BigDecimal("0.18"));
        settings.setShippingFlatRate(new BigDecimal("10.00"));
        settings.setReservationDepositAmount(BigDecimal.ZERO);
        settings.setReservationExpirationDays(1);
        settings.setPlan(Plan.ECOMMERCE);
        settings.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
        settings.setUpdatedAt(LocalDateTime.now());
        return companySettingsRepository.save(settings).getId();
    }

    private void crearUsuarioEnTenant(Long tenantId, String username) {
        TenantContext.set(tenantId);
        try {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPasswordHash(passwordEncoder.encode("ClaveValida123"));
            usuario.setFullName("Staff Segundo Negocio");
            usuario.setStatus(UsuarioEstado.ACTIVE);
            usuario.setRoles(new HashSet<>());
            usuarioRepository.save(usuario);
        } finally {
            TenantContext.clear();
        }
    }
}
