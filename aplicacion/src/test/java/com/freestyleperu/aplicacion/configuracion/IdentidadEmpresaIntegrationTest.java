package com.freestyleperu.aplicacion.configuracion;

import static org.assertj.core.api.Assertions.assertThat;

import com.freestyleperu.aplicacion.auth.dto.LoginRequest;
import com.freestyleperu.aplicacion.auth.dto.LoginResponse;
import com.freestyleperu.aplicacion.configuracion.domain.CompanySettings;
import com.freestyleperu.aplicacion.configuracion.domain.Plan;
import com.freestyleperu.aplicacion.configuracion.domain.SubscriptionStatus;
import com.freestyleperu.aplicacion.configuracion.repository.CompanySettingsRepository;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.usuario.domain.Permiso;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.PermisoRepository;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * RN-26: la identidad de la empresa (razón social, RUC, dirección, contacto,
 * logo) requiere CONFIGURACION_IDENTIDAD_EDITAR, separado de CONFIGURACION_EDITAR
 * (datos operativos: moneda, IGV, envío, pie de ticket) — un rol de cliente
 * puede tener el segundo sin el primero.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class IdentidadEmpresaIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private CompanySettingsRepository companySettingsRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private PermisoRepository permisoRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void restaurarConfiguracionCompartida() {
        // Esta prueba muta company_settings sin transacción (contexto RANDOM_PORT
        // compartido con otras pruebas) — se restaura al valor que las demás esperan.
        companySettingsRepository.findById(1L).ifPresent(settings -> {
            settings.setCurrencyCode("PEN");
            settings.setCurrencySymbol("S/");
            settings.setIgvRate(new BigDecimal("0.18"));
            settings.setShippingFlatRate(new BigDecimal("15.00"));
            settings.setReservationDepositAmount(new BigDecimal("20.00"));
            settings.setReservationExpirationDays(3);
            companySettingsRepository.save(settings);
        });
    }

    @Test
    void puedeEditarDatosOperativosPeroNoIdentidadNiLogoSinElPermisoDeIdentidad() {
        asegurarConfiguracionEmpresa();
        String token = crearUsuarioYLoguear("jefe.identidad",
                Set.of(Permisos.CONFIGURACION_VER, Permisos.CONFIGURACION_EDITAR));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Operativo: sí puede.
        var operativo = new java.util.HashMap<String, Object>();
        operativo.put("currencyCode", "PEN");
        operativo.put("currencySymbol", "S/");
        operativo.put("igvRate", new BigDecimal("0.18"));
        operativo.put("ticketFooter", "Gracias");
        operativo.put("shippingFlatRate", new BigDecimal("12.00"));
        operativo.put("reservationDepositAmount", new BigDecimal("20.00"));
        operativo.put("reservationExpirationDays", 3);
        ResponseEntity<String> respOperativo = restTemplate.exchange(
                "/api/settings/company", HttpMethod.PUT, new HttpEntity<>(operativo, headers), String.class);
        assertThat(respOperativo.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Identidad: no puede.
        var identidad = new java.util.HashMap<String, Object>();
        identidad.put("name", "Otro Nombre");
        identidad.put("ruc", "20999999999");
        identidad.put("address", "Otra dirección");
        identidad.put("phone", "900000000");
        identidad.put("email", "otro@test.com");
        identidad.put("businessVertical", "CLOTHING");
        ResponseEntity<String> respIdentidad = restTemplate.exchange(
                "/api/settings/company/identity", HttpMethod.PUT, new HttpEntity<>(identidad, headers), String.class);
        assertThat(respIdentidad.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Logo: tampoco.
        HttpHeaders headersMultipart = new HttpHeaders();
        headersMultipart.set("Authorization", "Bearer " + token);
        headersMultipart.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[] { 1, 2, 3, 4 }) {
            @Override
            public String getFilename() {
                return "logo.png";
            }
        });
        ResponseEntity<String> respLogo = restTemplate.exchange(
                "/api/settings/company/logo", HttpMethod.POST, new HttpEntity<>(body, headersMultipart), String.class);
        assertThat(respLogo.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void elEndpointDeMarcaEsPublicoYSoloExponeNombreYLogo() {
        asegurarConfiguracionEmpresa();

        ResponseEntity<Map<String, Object>> respuesta = restTemplate.exchange(
                "/api/system/branding", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<Map<String, Object>>() { });

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = respuesta.getBody();
        // logoUrl viaja solo si no es null (default-property-inclusion: non_null) —
        // lo importante es que NO se filtre nada de CompanySettingsResponse (ruc, phone, email...).
        assertThat(body.keySet()).isSubsetOf(Set.of("name", "logoUrl"));
        assertThat(body.get("name")).isNotNull();
    }

    private void asegurarConfiguracionEmpresa() {
        if (companySettingsRepository.existsById(1L)) {
            return;
        }
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

    private String crearUsuarioYLoguear(String username, Set<String> codigosPermiso) {
        Set<Permiso> permisos = new HashSet<>();
        for (String codigo : codigosPermiso) {
            Permiso permiso = permisoRepository.findAll().stream()
                    .filter(p -> p.getCode().equals(codigo))
                    .findFirst()
                    .orElseGet(() -> permisoRepository.save(Permiso.builder().code(codigo).module("TEST").description(codigo).build()));
            permisos.add(permiso);
        }

        Rol rol = new Rol();
        rol.setCode("TEST_ROL_IDENTIDAD_" + username.hashCode());
        rol.setName("Rol de prueba identidad");
        rol.setSystem(false);
        rol.setPermisos(permisos);
        rolRepository.save(rol);

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPasswordHash(passwordEncoder.encode("ClaveValida123"));
        usuario.setFullName("Usuario de Prueba Identidad");
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setRoles(new HashSet<>(Set.of(rol)));
        usuarioRepository.save(usuario);

        ResponseEntity<LoginResponse> respuesta = restTemplate.postForEntity(
                "/api/auth/login", new LoginRequest(username, "ClaveValida123"), LoginResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().accessToken();
    }
}
