package com.freestyleperu.aplicacion.notificacion.web;

import com.freestyleperu.aplicacion.notificacion.service.NotificacionService;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.shared.security.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams SSE de notificaciones — el token llega por query param (ver {@code JwtAuthenticationFilter}),
 * no por header, porque {@code EventSource} del navegador no puede mandar headers propios.
 */
@RestController
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping("/api/notifications/stream")
    @PreAuthorize("hasAuthority('" + Permisos.PEDIDOS_CONSULTAR + "') and @modulos.activo('TIENDA')")
    public SseEmitter streamStaff() {
        return notificacionService.suscribirStaff(TenantContext.getOrDefault());
    }

    @GetMapping("/api/store/notifications/stream")
    @PreAuthorize("hasAuthority('" + Permisos.ROLE_CUSTOMER + "')")
    public SseEmitter streamCliente(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return notificacionService.suscribirCliente(currentUser.id());
    }

    @GetMapping("/api/store/catalog/stream")
    public SseEmitter streamCatalogo() {
        return notificacionService.suscribirCatalogo(TenantContext.getOrDefault());
    }
}
