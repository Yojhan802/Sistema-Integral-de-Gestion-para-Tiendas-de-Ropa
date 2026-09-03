package com.freestyleperu.aplicacion.plataforma;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Los comprobantes de suscripción son capturas de Yape o de transferencias: llevan nombres
 * y montos. El resto de {@code /uploads} es público porque la tienda necesita mostrar
 * productos y logos sin sesión, así que la excepción hay que fijarla explícitamente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class ComprobanteSuscripcionSeguridadTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void laRutaEstaticaDeLosComprobantesNoEsAlcanzableSinSesion() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity(
                "/uploads/suscripciones/cualquiera.jpg", String.class);

        // denyAll: no existe camino por la ruta estática, ni siquiera autenticado.
        assertThat(respuesta.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void elRestoDeUploadsSigueSiendoPublicoParaLaTienda() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity(
                "/uploads/products/inexistente.webp", String.class);

        // Lo que importa es que la seguridad no lo corte: el archivo no existe, así que el
        // código concreto depende de si hay carpeta de uploads, pero nunca debe ser 401/403.
        assertThat(respuesta.getStatusCode()).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void elEndpointDelComprobanteExigeSerOperadorDePlataforma() {
        ResponseEntity<String> respuesta = restTemplate.getForEntity(
                "/api/platform/tenants/1/subscription/payments/1/proof", String.class);

        assertThat(respuesta.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
