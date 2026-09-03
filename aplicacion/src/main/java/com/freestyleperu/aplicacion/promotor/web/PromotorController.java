package com.freestyleperu.aplicacion.promotor.web;

import com.freestyleperu.aplicacion.promotor.dto.request.PromoterRequest;
import com.freestyleperu.aplicacion.promotor.dto.response.PromoterResponse;
import com.freestyleperu.aplicacion.promotor.service.PromoterService;
import com.freestyleperu.aplicacion.shared.dto.CambiarEstadoRequest;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromotorController {

    private final PromoterService promoterService;

    public PromotorController(PromoterService promoterService) {
        this.promoterService = promoterService;
    }

    @GetMapping("/api/promoters")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOTORES_CONSULTAR + "') and @modulos.activo('PROMOTORES')")
    public List<PromoterResponse> listar() {
        return promoterService.listar();
    }

    @PostMapping("/api/promoters")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOTORES_GESTIONAR + "') and @modulos.activo('PROMOTORES')")
    public ResponseEntity<PromoterResponse> crear(@Valid @RequestBody PromoterRequest request) {
        PromoterResponse creado = promoterService.crear(request);
        return ResponseEntity.created(URI.create("/api/promoters/" + creado.id())).body(creado);
    }

    @PutMapping("/api/promoters/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOTORES_GESTIONAR + "') and @modulos.activo('PROMOTORES')")
    public PromoterResponse actualizar(@PathVariable Long id, @Valid @RequestBody PromoterRequest request) {
        return promoterService.actualizar(id, request);
    }

    @PatchMapping("/api/promoters/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOTORES_GESTIONAR + "') and @modulos.activo('PROMOTORES')")
    public PromoterResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return promoterService.cambiarEstado(id, request.status());
    }
}
