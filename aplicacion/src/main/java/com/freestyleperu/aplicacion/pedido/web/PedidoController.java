package com.freestyleperu.aplicacion.pedido.web;

import com.freestyleperu.aplicacion.pedido.domain.PedidoStatus;
import com.freestyleperu.aplicacion.pedido.dto.request.CancelarPedidoRequest;
import com.freestyleperu.aplicacion.pedido.dto.response.PedidoResponse;
import com.freestyleperu.aplicacion.pedido.dto.response.PedidoResumenResponse;
import com.freestyleperu.aplicacion.pedido.service.PedidoService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Vista de staff sobre los pedidos de la tienda online — ver también {@code tienda.web.TiendaPedidoController} para el lado cliente. */
@RestController
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping("/api/orders")
    @PreAuthorize("hasAuthority('" + Permisos.PEDIDOS_CONSULTAR + "') and @modulos.activo('TIENDA')")
    public PageResponse<PedidoResumenResponse> listar(
            @RequestParam(required = false) PedidoStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Pageable pageable) {
        return pedidoService.listar(status, from, to, pageable);
    }

    @GetMapping("/api/orders/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PEDIDOS_CONSULTAR + "') and @modulos.activo('TIENDA')")
    public PedidoResponse obtener(@PathVariable Long id) {
        return pedidoService.obtener(id);
    }

    @PostMapping("/api/orders/{id}/confirm")
    @PreAuthorize("hasAuthority('" + Permisos.PEDIDOS_GESTIONAR + "') and @modulos.activo('TIENDA')")
    public PedidoResponse confirmar(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return pedidoService.confirmarPago(id, currentUser.id());
    }

    @PostMapping("/api/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('" + Permisos.PEDIDOS_GESTIONAR + "') and @modulos.activo('TIENDA')")
    public PedidoResponse cancelar(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CancelarPedidoRequest request) {
        return pedidoService.cancelar(id, request, currentUser.id());
    }
}
