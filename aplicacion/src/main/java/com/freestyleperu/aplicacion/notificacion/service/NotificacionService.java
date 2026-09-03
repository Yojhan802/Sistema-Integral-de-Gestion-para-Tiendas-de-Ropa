package com.freestyleperu.aplicacion.notificacion.service;

import com.freestyleperu.aplicacion.facturacion.dto.response.ElectronicDocumentResponse;
import com.freestyleperu.aplicacion.pedido.dto.response.PedidoResponse;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Empuja eventos de pedidos a los navegadores conectados vía SSE — sin cola/broker externo,
 * solo listas en memoria (aceptable porque un reinicio del proceso igual tumba las conexiones
 * SSE abiertas, así que no hay estado que sobreviva un restart de todas formas). Pensado para
 * que agregar WhatsApp/email más adelante sea sumar otro "suscriptor" a estos mismos eventos,
 * no modificar quien los dispara ({@code PedidoService}).
 */
@Service
public class NotificacionService {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * Puente de compatibilidad para consumidores internos antiguos. Las conexiones HTTP nuevas
     * usan siempre {@link #staffEmittersPorTenant}; esta lista solo evita romper extensiones/tests
     * que inyectaban un emitter antes de introducir el aislamiento por tenant.
     */
    @SuppressWarnings("unused")
    private final List<SseEmitter> staffEmitters = new CopyOnWriteArrayList<>();
    private final Map<Long, List<SseEmitter>> staffEmittersPorTenant = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> emittersPorCliente = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> catalogoEmittersPorTenant = new ConcurrentHashMap<>();

    public SseEmitter suscribirStaff(Long tenantId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> lista = staffEmittersPorTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
        emitter.onCompletion(() -> retirar(staffEmittersPorTenant, tenantId, emitter));
        emitter.onTimeout(() -> retirar(staffEmittersPorTenant, tenantId, emitter));
        emitter.onError(e -> retirar(staffEmittersPorTenant, tenantId, emitter));
        lista.add(emitter);
        enviarInicio(emitter, "staff");
        return emitter;
    }

    public SseEmitter suscribirCliente(Long customerId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> lista = emittersPorCliente.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>());
        emitter.onCompletion(() -> lista.remove(emitter));
        emitter.onTimeout(() -> lista.remove(emitter));
        emitter.onError(e -> lista.remove(emitter));
        lista.add(emitter);
        enviarInicio(emitter, "cliente");
        return emitter;
    }

    public SseEmitter suscribirCatalogo(Long tenantId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> lista = catalogoEmittersPorTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
        emitter.onCompletion(() -> retirar(catalogoEmittersPorTenant, tenantId, emitter));
        emitter.onTimeout(() -> retirar(catalogoEmittersPorTenant, tenantId, emitter));
        emitter.onError(e -> retirar(catalogoEmittersPorTenant, tenantId, emitter));
        lista.add(emitter);
        enviarInicio(emitter, "catalogo");
        return emitter;
    }

    public void notificarPedidoNuevo(PedidoResponse pedido) {
        Long tenantId = TenantContext.getOrDefault();
        List<SseEmitter> lista = staffEmittersPorTenant.get(tenantId);
        if (lista != null) {
            despuesDelCommit(() -> enviarATodos(lista, "pedido-nuevo", pedido));
        }
        // Compatibilidad con la API interna anterior, que no conocía el tenant.
        if (tenantId.equals(TenantContext.DEFAULT_TENANT_ID) && !staffEmitters.isEmpty()) {
            enviarATodos(staffEmitters, "pedido-nuevo", pedido);
        }
    }

    public void notificarPedidoActualizado(Long customerId, PedidoResponse pedido) {
        Long tenantId = TenantContext.getOrDefault();
        List<SseEmitter> staff = staffEmittersPorTenant.get(tenantId);
        if (staff != null) {
            despuesDelCommit(() -> enviarATodos(staff, "pedido-actualizado", pedido));
        }
        List<SseEmitter> lista = emittersPorCliente.get(customerId);
        if (lista != null) {
            // Se conserva la entrega inmediata que ya usaba el storefront para actualizar el
            // estado del pedido mientras el cliente mantiene abierta la pantalla.
            enviarATodos(lista, "pedido-actualizado", pedido);
        }
    }

    /**
     * Publica el resultado de una consulta/emision de comprobante sin exponer
     * credenciales ni el payload fiscal. El mismo evento alimenta el panel del
     * negocio y la pantalla de pedidos del cliente.
     */
    public void notificarComprobanteActualizado(Long customerId, ElectronicDocumentResponse documento) {
        Long tenantId = TenantContext.getOrDefault();
        Runnable action = () -> {
            List<SseEmitter> staff = staffEmittersPorTenant.get(tenantId);
            if (staff != null) {
                enviarATodos(staff, "comprobante-actualizado", documento);
            }
            if (customerId != null) {
                List<SseEmitter> customer = emittersPorCliente.get(customerId);
                if (customer != null) {
                    enviarATodos(customer, "comprobante-actualizado", documento);
                }
            }
        };
        despuesDelCommit(action);
    }

    public void notificarCatalogoActualizado(Long tenantId) {
        List<SseEmitter> lista = catalogoEmittersPorTenant.get(tenantId);
        if (lista != null) {
            enviarATodos(lista, "catalogo-actualizado", Map.of("tenantId", tenantId, "revision", System.currentTimeMillis()));
        }
    }

    private <K> void retirar(Map<K, List<SseEmitter>> grupos, K clave, SseEmitter emitter) {
        List<SseEmitter> lista = grupos.get(clave);
        if (lista != null) {
            lista.remove(emitter);
            if (lista.isEmpty()) grupos.remove(clave, lista);
        }
    }

    private void despuesDelCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /**
     * Latido periódico sobre todos los canales abiertos.
     *
     * <p>Sin él, un cliente que desaparece sin cerrar (una pestaña que se cierra, un móvil
     * que bloquea la pantalla, una red que se cae) deja su conexión ocupada hasta el timeout
     * de 30 minutos: como nadie escribe en el stream, el servidor nunca descubre que el
     * socket está muerto. Con suficientes clientes, Tomcat llega a su límite de conexiones
     * y deja de aceptar peticiones — el proceso sigue vivo pero no responde nada.
     *
     * <p>Se envía un comentario SSE, que {@code EventSource} ignora: no llega al cliente
     * como evento, pero su escritura falla en cuanto el cliente ya no está, y ahí sí se
     * cierra el emitter y se libera la conexión.
     */
    @Scheduled(fixedDelayString = "${app.notificaciones.heartbeat-ms:20000}")
    public void enviarLatido() {
        Stream.of(staffEmittersPorTenant, emittersPorCliente, catalogoEmittersPorTenant)
                .flatMap(grupos -> grupos.values().stream())
                .forEach(this::latirEn);
        latirEn(staffEmitters);
    }

    private void latirEn(List<SseEmitter> emitters) {
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                descartar(emitters, emitter, e);
            }
        }
    }

    private void enviarATodos(List<SseEmitter> emitters, String evento, Object datos) {
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name(evento).data(datos));
            } catch (IOException | IllegalStateException e) {
                // Cliente desconectado o emitter ya cerrado — nunca debe tumbar la transacción que originó el evento.
                descartar(emitters, emitter, e);
            }
        }
    }

    /**
     * Quitarlo de la lista no basta: mientras la petición asíncrona no se complete, Tomcat
     * mantiene ocupada la conexión. Hay que cerrarla explícitamente.
     */
    private void descartar(List<SseEmitter> emitters, SseEmitter emitter, Exception causa) {
        emitters.remove(emitter);
        try {
            emitter.completeWithError(causa);
        } catch (RuntimeException yaCerrado) {
            // El emitter ya había terminado; los callbacks onError/onCompletion ya lo retiraron.
        }
    }

    private void enviarInicio(SseEmitter emitter, String canal) {
        try {
            emitter.send(SseEmitter.event().name("stream-ready").data(Map.of("channel", canal)));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }
}
