package com.freestyleperu.aplicacion.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.freestyleperu.aplicacion.auth.dto.LoginRequest;
import com.freestyleperu.aplicacion.auth.dto.LoginResponse;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.dto.response.SystemInfoResponse;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.usuario.domain.Permiso;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.dto.request.CrearUsuarioRequest;
import com.freestyleperu.aplicacion.usuario.repository.PermisoRepository;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import com.freestyleperu.aplicacion.usuario.service.UsuarioService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * Verifica que @planGate bloquee rutas por plan de suscripción a nivel HTTP
 * real (mismo enfoque que SeguridadIntegrationTest, no contra los servicios
 * directamente) — ver docs/03-modelo-datos.md §15 y RN-23.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class PlanGateIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PermisoRepository permisoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UsuarioService usuarioService;

    @AfterEach
    void restablecerPlan() {
        establecerPlan(Plan.ECOMMERCE);
        establecerEstadoSuscripcion(SubscriptionStatus.ACTIVA);
    }

    @Test
    void elCatalogoPublicoDeLaTiendaRequierePlanEcommerce() {
        establecerPlan(Plan.STARTER);
        ResponseEntity<String> bloqueado = restTemplate.getForEntity("/api/store/catalog/categories", String.class);
        assertThat(bloqueado.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        establecerPlan(Plan.ECOMMERCE);
        ResponseEntity<String> permitido = restTemplate.getForEntity("/api/store/catalog/categories", String.class);
        assertThat(permitido.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registroDeClientesDeLaTiendaRequierePlanEcommerce() {
        Map<String, String> body = Map.of(
                "email", "plangate.cliente@test.com",
                "password", "clave1234",
                "fullName", "Cliente PlanGate",
                "phone", "999000111");

        establecerPlan(Plan.STARTER);
        assertThat(restTemplate.postForEntity("/api/store/auth/register", body, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        establecerPlan(Plan.ECOMMERCE);
        assertThat(restTemplate.postForEntity("/api/store/auth/register", body, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void pedidosDeStaffRequierenPlanEcommercePorEncimaDelPermiso() {
        crearUsuario("plangate.pedidos", "ClaveValida123", Set.of(Permisos.PEDIDOS_CONSULTAR));
        String token = obtenerAccessToken("plangate.pedidos", "ClaveValida123");

        establecerPlan(Plan.PROFESIONAL);
        assertThat(get("/api/orders", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        establecerPlan(Plan.ECOMMERCE);
        assertThat(get("/api/orders", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void promotoresYAuditoriaRequierenPlanProfesionalPorEncimaDelPermiso() {
        crearUsuario("plangate.profesional", "ClaveValida123",
                Set.of(Permisos.PROMOTORES_CONSULTAR, Permisos.AUDITORIA_CONSULTAR));
        String token = obtenerAccessToken("plangate.profesional", "ClaveValida123");

        establecerPlan(Plan.STARTER);
        assertThat(get("/api/promoters", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/audit", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        establecerPlan(Plan.PROFESIONAL);
        assertThat(get("/api/promoters", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/audit", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void separacionesRequierenPlanProfesionalPorEncimaDelPermiso() {
        crearUsuario("plangate.separaciones", "ClaveValida123", Set.of(Permisos.RESERVAS_CONSULTAR));
        String token = obtenerAccessToken("plangate.separaciones", "ClaveValida123");

        establecerPlan(Plan.STARTER);
        assertThat(get("/api/reservations", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        establecerPlan(Plan.PROFESIONAL);
        assertThat(get("/api/reservations", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void combosYPromocionesRequierenPlanProfesionalPorEncimaDelPermiso() {
        crearUsuario("plangate.comboypromo", "ClaveValida123",
                Set.of(Permisos.COMBOS_CONSULTAR, Permisos.PROMOCIONES_CONSULTAR));
        String token = obtenerAccessToken("plangate.comboypromo", "ClaveValida123");

        establecerPlan(Plan.STARTER);
        assertThat(get("/api/combos", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/api/promotions", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        establecerPlan(Plan.PROFESIONAL);
        assertThat(get("/api/combos", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/promotions", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void elPlanStarterLimitaLosUsuariosActivosYLosDemasPlanesNo() {
        establecerPlan(Plan.STARTER);
        // Se asegura llegar exactamente al límite (3), sin importar cuántos usuarios
        // hayan quedado de otras pruebas que comparten esta misma base H2.
        Rol rolBase = rolBase();
        Long actorId = usuarioRepository.save(usuarioDirecto("plangate.actor", rolBase)).getId();
        while (usuarioRepository.count() < 3) {
            usuarioRepository.save(usuarioDirecto("plangate.relleno." + usuarioRepository.count(), rolBase));
        }

        assertThatThrownBy(() -> usuarioService.crear(new CrearUsuarioRequest(
                "plangate.rechazado", "plangate.rechazado@test.com", "ClaveValida123", "Rechazado por límite",
                null, null, List.of(rolBase.getId())), actorId))
                .isInstanceOf(OperacionNoPermitidaException.class);

        establecerPlan(Plan.PROFESIONAL);
        var creado = usuarioService.crear(new CrearUsuarioRequest(
                "plangate.aceptado", "plangate.aceptado@test.com", "ClaveValida123", "Aceptado sin límite",
                null, null, List.of(rolBase.getId())), actorId);
        assertThat(creado.username()).isEqualTo("plangate.aceptado");
    }

    @Test
    void laSuscripcionSuspendidaBloqueaTodoMenosLasRutasExentas() {
        crearUsuario("plangate.suspendido", "ClaveValida123", Set.of(Permisos.PEDIDOS_CONSULTAR));
        String token = obtenerAccessToken("plangate.suspendido", "ClaveValida123");
        establecerPlan(Plan.ECOMMERCE);

        establecerEstadoSuscripcion(SubscriptionStatus.SUSPENDIDA);
        // Bloquea tienda pública y API de staff por igual, aunque el plan y el permiso alcancen.
        assertThat(restTemplate.getForEntity("/api/store/catalog/categories", String.class).getStatusCode().value())
                .isEqualTo(402);
        assertThat(get("/api/orders", token).getStatusCode().value()).isEqualTo(402);
        // Las rutas exentas siguen respondiendo con normalidad — incluida la marca pública,
        // porque la propia pantalla de "servicio suspendido" necesita pintar el logo correcto.
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/api/system/info", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/api/system/branding", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.postForEntity("/api/auth/login", new LoginRequest("plangate.suspendido", "ClaveValida123"), LoginResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        establecerEstadoSuscripcion(SubscriptionStatus.ACTIVA);
        assertThat(get("/api/orders", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void soloLaLlaveOpsCorrectaPuedeCambiarLaSuscripcionYFuncionaAunSuspendida() {
        establecerEstadoSuscripcion(SubscriptionStatus.SUSPENDIDA);

        assertThat(putSuscripcion(null, "ACTIVA", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(putSuscripcion("llave-incorrecta", "ACTIVA", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // La llave correcta funciona incluso con la suscripción ya SUSPENDIDA — si no, nunca se podría reactivar.
        ResponseEntity<SystemInfoResponse> reactivado = putSuscripcion("test-ops-key-1234", "ACTIVA", "2026-12-01");
        assertThat(reactivado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reactivado.getBody().subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVA);
        assertThat(reactivado.getBody().nextPaymentDue()).isEqualTo(LocalDate.of(2026, 12, 1));
    }

    private ResponseEntity<SystemInfoResponse> putSuscripcion(String opsKey, String status, String nextPaymentDue) {
        HttpHeaders headers = new HttpHeaders();
        if (opsKey != null) headers.set("X-Ops-Key", opsKey);
        Map<String, Object> body = new HashMap<>();
        body.put("tenantId", 1L);
        body.put("subscriptionStatus", status);
        body.put("nextPaymentDue", nextPaymentDue);
        return restTemplate.exchange(
                "/api/system/subscription", HttpMethod.PUT, new HttpEntity<>(body, headers), SystemInfoResponse.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private void establecerPlan(Plan plan) {
        CompanySettings settings = companySettingsRepository.findById(1L).orElseGet(() -> {
            CompanySettings nuevo = new CompanySettings();
            nuevo.setSlug("default");
            nuevo.setName("Freestyle Perú (semilla test)");
            nuevo.setCurrencyCode("PEN");
            nuevo.setCurrencySymbol("S/");
            nuevo.setIgvRate(new BigDecimal("0.18"));
            nuevo.setShippingFlatRate(new BigDecimal("15.00"));
            nuevo.setReservationDepositAmount(new BigDecimal("20.00"));
            nuevo.setReservationExpirationDays(3);
            nuevo.setSubscriptionStatus(SubscriptionStatus.ACTIVA);
            nuevo.setUpdatedAt(LocalDateTime.now());
            return nuevo;
        });
        settings.setPlan(plan);
        companySettingsRepository.save(settings);
    }

    private void establecerEstadoSuscripcion(SubscriptionStatus status) {
        CompanySettings settings = companySettingsRepository.findById(1L).orElseThrow();
        settings.setSubscriptionStatus(status);
        companySettingsRepository.save(settings);
    }

    private String obtenerAccessToken(String username, String password) {
        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, password), LoginResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().accessToken();
    }

    private Rol rolBase() {
        Rol rol = new Rol();
        rol.setCode("TEST_ROL_PG_" + (System.nanoTime() % 1_000_000));
        rol.setName("Rol de prueba PlanGate");
        rol.setSystem(false);
        rol.setHierarchyLevel((short) 100); // evita chocar con RN-25 en pruebas ajenas a ese tema
        return rolRepository.save(rol);
    }

    private Usuario usuarioDirecto(String username, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPasswordHash(passwordEncoder.encode("ClaveValida123"));
        usuario.setFullName("Relleno PlanGate");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        return usuario;
    }

    private void crearUsuario(String username, String password, Set<String> codigosPermiso) {
        Set<Permiso> permisos = new HashSet<>();
        for (String codigo : codigosPermiso) {
            Permiso permiso = permisoRepository.findAll().stream()
                    .filter(p -> p.getCode().equals(codigo))
                    .findFirst()
                    .orElseGet(() -> permisoRepository.save(Permiso.builder().code(codigo).module("TEST").description(codigo).build()));
            permisos.add(permiso);
        }

        Rol rol = new Rol();
        rol.setCode("TEST_ROL_PG_" + username.hashCode());
        rol.setName("Rol de prueba PlanGate");
        rol.setSystem(false);
        rol.setPermisos(permisos);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setFullName("Usuario de Prueba PlanGate");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(List.of(rol)));
        usuarioRepository.save(usuario);
    }
}
