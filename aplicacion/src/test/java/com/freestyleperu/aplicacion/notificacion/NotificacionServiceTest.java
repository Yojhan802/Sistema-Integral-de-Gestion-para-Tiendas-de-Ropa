package com.freestyleperu.aplicacion.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.freestyleperu.aplicacion.notificacion.service.NotificacionService;
import com.freestyleperu.aplicacion.pedido.domain.PedidoStatus;
import com.freestyleperu.aplicacion.pedido.dto.response.PedidoResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * NotificacionService mantiene sus listas de emitters como campos privados (por diseño — no hay
 * API para "ver quién está suscrito", solo para suscribirse/notificar), así que estos tests
 * inyectan un {@link SseEmitter} de prueba directamente en esas listas por reflexión en vez de
 * pasar por {@code suscribirStaff()} (que crea un emitter real inutilizable sin un
 * {@code HttpServletResponse} detrás).
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificacionServiceTest {

    @Autowired
    private NotificacionService notificacionService;

    @Test
    void notificarPedidoNuevoLlegaATodosLosEmittersDeStaffSuscritos() throws Exception {
        CapturingEmitter capturador = new CapturingEmitter();
        inyectarStaffEmitter(capturador);

        notificacionService.notificarPedidoNuevo(pedidoDePrueba());

        assertThat(capturador.eventosRecibidos).isEqualTo(1);
    }

    @Test
    void unEmitterQueFallaAlEscribirSeSacaDeLaListaSinPropagarElError() throws Exception {
        CapturingEmitter fallando = new CapturingEmitter();
        fallando.fallarSiempre = true;
        inyectarStaffEmitter(fallando);

        assertThatCode(() -> notificacionService.notificarPedidoNuevo(pedidoDePrueba())).doesNotThrowAnyException();

        // El emitter ya se sacó de la lista tras fallar — una segunda notificación no vuelve a tocarlo.
        notificacionService.notificarPedidoNuevo(pedidoDePrueba());
        assertThat(fallando.intentosDeEnvio).isEqualTo(1);
    }

    /**
     * Un cliente que desaparece sin cerrar no genera tráfico, así que sin latido el servidor
     * nunca descubre que el socket murió y la conexión queda ocupada hasta el timeout de 30
     * minutos. Con suficientes clientes eso agota el límite de conexiones de Tomcat.
     */
    @Test
    void elLatidoDetectaAlClienteMuertoYCierraSuEmitter() throws Exception {
        CapturingEmitter muerto = new CapturingEmitter();
        muerto.fallarSiempre = true;
        inyectarStaffEmitter(muerto);

        notificacionService.enviarLatido();

        assertThat(muerto.intentosDeEnvio).isEqualTo(1);
        // Quitarlo de la lista no basta: si la petición asíncrona no se completa, Tomcat
        // mantiene ocupada la conexión. Por eso se exige el cierre explícito.
        assertThat(muerto.cerrado).isTrue();

        // Ya no forma parte del canal: el siguiente latido no vuelve a intentarlo.
        notificacionService.enviarLatido();
        assertThat(muerto.intentosDeEnvio).isEqualTo(1);
    }

    @Test
    void elLatidoNoLlegaAlClienteComoEventoNiTumbaAlQueSigueVivo() throws Exception {
        CapturingEmitter vivo = new CapturingEmitter();
        inyectarStaffEmitter(vivo);

        notificacionService.enviarLatido();

        assertThat(vivo.intentosDeEnvio).isEqualTo(1);
        assertThat(vivo.cerrado).isFalse();
        // Se envía como comentario SSE: EventSource lo ignora, así que el cliente no
        // recibe un evento espurio cada 20 segundos.
        assertThat(vivo.eventosConNombre).isZero();
    }

    @SuppressWarnings("unchecked")
    private void inyectarStaffEmitter(SseEmitter emitter) throws Exception {
        Field field = NotificacionService.class.getDeclaredField("staffEmitters");
        field.setAccessible(true);
        ((List<SseEmitter>) field.get(notificacionService)).add(emitter);
    }

    private PedidoResponse pedidoDePrueba() {
        return new PedidoResponse(
                1L, "PED-TEST", 1L, "Cliente Test",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                PedidoStatus.PENDING_PAYMENT,
                1L, "Yape", null, null,
                null, null, null, null,
                null, null, null, null, null, null,
                LocalDateTime.now(), null, null, null, null,
                null, List.of());
    }

    private static class CapturingEmitter extends SseEmitter {
        int eventosRecibidos = 0;
        int intentosDeEnvio = 0;
        int eventosConNombre = 0;
        boolean fallarSiempre = false;
        boolean cerrado = false;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            intentosDeEnvio++;
            if (fallarSiempre) {
                throw new IOException("simulado");
            }
            // Un evento con nombre serializa "event:<nombre>"; un comentario, solo ":texto".
            if (builder.build().stream().anyMatch(parte -> String.valueOf(parte).startsWith("event:"))) {
                eventosConNombre++;
            }
            eventosRecibidos++;
        }

        @Override
        public void completeWithError(Throwable ex) {
            cerrado = true;
        }

        @Override
        public void complete() {
            cerrado = true;
        }
    }
}
