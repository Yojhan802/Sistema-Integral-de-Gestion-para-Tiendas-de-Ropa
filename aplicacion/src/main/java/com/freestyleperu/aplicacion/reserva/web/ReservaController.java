package com.freestyleperu.aplicacion.reserva.web;

import com.freestyleperu.aplicacion.reserva.domain.ReservaStatus;
import com.freestyleperu.aplicacion.reserva.dto.request.CancelarReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CompletarReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CompletarVariasReservasRequest;
import com.freestyleperu.aplicacion.reserva.dto.request.CrearReservaRequest;
import com.freestyleperu.aplicacion.reserva.dto.response.PreviewCompletarVariasResponse;
import com.freestyleperu.aplicacion.reserva.dto.response.ReservaResponse;
import com.freestyleperu.aplicacion.reserva.dto.response.ReservaResumenResponse;
import com.freestyleperu.aplicacion.reserva.service.ReservaService;
import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Separaciones (layaway) — funcionalidad de plan PROFESIONAL, ver docs/03 §17.
 * El plan se combina con el permiso en cada método (no alcanza con ponerlo
 * a nivel de clase: un {@code @PreAuthorize} de método reemplaza por
 * completo al de clase, no se combinan).
 */
@RestController
public class ReservaController {

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @GetMapping("/api/reservations")
    @PreAuthorize("hasAuthority('" + Permisos.RESERVAS_CONSULTAR + "') and @modulos.activo('SEPARACIONES')")
    public PageResponse<ReservaResumenResponse> listar(
            @RequestParam(required = false) ReservaStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String buyerName,
            Pageable pageable) {
        return reservaService.listar(status, customerId, buyerName, pageable);
    }

    @GetMapping("/api/reservations/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.RESERVAS_CONSULTAR + "') and @modulos.activo('SEPARACIONES')")
    public ReservaResponse obtener(@PathVariable Long id) {
        return reservaService.obtener(id);
    }

    @PostMapping("/api/reservations")
    @PreAuthorize("hasAuthority('" + Permisos.RESERVAS_CREAR + "') and @modulos.activo('SEPARACIONES')")
    public ResponseEntity<ReservaResponse> crear(
            @Valid @RequestBody CrearReservaRequest request, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        ReservaResponse creada = reservaService.crear(request, currentUser.id());
        return ResponseEntity.created(URI.create("/api/reservations/" + creada.id())).body(creada);
    }

    @PostMapping("/api/reservations/{id}/complete")
    @PreAuthorize("hasAuthority('" + Permisos.RESERVAS_GESTIONAR + "') and @modulos.activo('SEPARACIONES')")
    public ReservaResponse completar(
            @PathVariable Long id,
            @Valid @RequestBody CompletarReservaRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return reservaService.completar(id, request, currentUser.id());
    }

    /** Previsualiza el cobro de varias separaciones juntas (con combo ya detectado si aplica) antes de pedir los pagos. */
    @GetMapping("/api/reservations/complete-batch/preview")
    @PreAuthorize("hasAuthority('" + Permisos.RESERVAS_GESTIONAR + "') and @modulos.activo('SEPARACIONES')")
    public PreviewCompletarVariasResponse previsualizarCompletarVarias(@RequestParam List<Long> ids) {
        return reservaService.previsualizarCompletarVarias(ids);
    }

    /** Completa varias separaciones del mismo comprador de una sola vez, con detección automática de combo (RN-28). */
    @PostMapping("/api/reservations/complete-batch")
    @PreAuthorize("hasAuthority('" + Permisos.RESERVAS_GESTIONAR + "') and @modulos.activo('SEPARACIONES')")
    public List<ReservaResponse> completarVarias(
            @Valid @RequestBody CompletarVariasReservasRequest request, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return reservaService.completarVarias(request, currentUser.id());
    }

    @PostMapping("/api/reservations/{id}/cancel")
    @PreAuthorize("hasAuthority('" + Permisos.RESERVAS_GESTIONAR + "') and @modulos.activo('SEPARACIONES')")
    public ReservaResponse cancelar(
            @PathVariable Long id,
            @Valid @RequestBody CancelarReservaRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return reservaService.cancelar(id, request, currentUser.id());
    }
}
