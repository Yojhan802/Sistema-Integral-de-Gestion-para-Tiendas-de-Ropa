package com.freestyleperu.aplicacion.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * El navegador envía la cabecera {@code Origin} también cuando la petición es del mismo
 * origen (lo hace en todo POST/PUT/DELETE), y Spring trata como CORS cualquier petición
 * que la lleve. Como los frontends se sirven detrás del mismo nginx que expone /api, eso
 * devolvía 403 al iniciar sesión desde cualquier host no enumerado en
 * {@code app.cors.allowed-origins}: la IP de LAN al probar desde un móvil, o un dominio
 * nuevo en producción.
 *
 * <p>Estas pruebas fijan las dos mitades del comportamiento: se admite el Origin que
 * coincide con el host por el que llegó la petición, y se sigue rechazando el de otro
 * dominio, que es lo que CORS debe impedir.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class CorsMismoOrigenIntegrationTest {

    /**
     * Se usa el login de staff porque es permitAll y además está exento del filtro de
     * suscripción: así un 403 solo puede venir de CORS, que es lo que se está midiendo.
     */
    private static final String LOGIN = "/api/auth/login";

    @Autowired private TestRestTemplate restTemplate;

    private ResponseEntity<String> login(String origin, String hostReenviado, String protocoloReenviado) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ORIGIN, origin);
        if (hostReenviado != null) {
            headers.set("X-Forwarded-Host", hostReenviado);
        }
        if (protocoloReenviado != null) {
            headers.set("X-Forwarded-Proto", protocoloReenviado);
        }
        return restTemplate.exchange(LOGIN, HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "nadie", "password", "Contrasena.1"), headers),
                String.class);
    }

    @Test
    void admiteElOrigenQueCoincideConElHostPorElQueLlegoLaPeticion() {
        ResponseEntity<String> respuesta = login("http://192.168.1.5:8093", "192.168.1.5:8093", "http");

        // Llega al controlador: las credenciales son falsas, así que responde 401, no 403.
        // Lo que se comprueba es que CORS ya no lo corta antes de autenticar.
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://192.168.1.5:8093");
    }

    @Test
    void admiteTambienUnDominioNuevoDetrasDeUnProxyConHttps() {
        ResponseEntity<String> respuesta = login("https://tienda.ejemplo.pe", "tienda.ejemplo.pe", "https");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isEqualTo("https://tienda.ejemplo.pe");
    }

    @Test
    void sigueRechazandoUnOrigenAjenoAlHostDeLaPeticion() {
        // Una página en otro dominio no puede falsear su Origin: el navegador lo fija.
        ResponseEntity<String> respuesta = login("http://sitio-malicioso.example", "192.168.1.5:8093", "http");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    void unOrigenQueSoloComparteElPrefijoDelHostNoSeConfundeConElMismoOrigen() {
        ResponseEntity<String> respuesta = login("http://192.168.1.5:8093.example.com", "192.168.1.5:8093", "http");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void elOrigenPermitidoPorConfiguracionSigueFuncionando() {
        // application-test.yml enumera http://localhost:5173 (el dev server de Vite),
        // que sí es un origen distinto del host de la petición.
        ResponseEntity<String> respuesta = login("http://localhost:5173", "192.168.1.5:8093", "http");

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
    }
}
