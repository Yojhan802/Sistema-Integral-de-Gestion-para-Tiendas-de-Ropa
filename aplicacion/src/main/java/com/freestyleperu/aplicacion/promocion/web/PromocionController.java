package com.freestyleperu.aplicacion.promocion.web;

import com.freestyleperu.aplicacion.promocion.dto.request.PromocionRequest;
import com.freestyleperu.aplicacion.promocion.dto.response.PromocionResponse;
import com.freestyleperu.aplicacion.promocion.service.PromocionService;
import com.freestyleperu.aplicacion.shared.dto.CambiarEstadoRequest;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Promociones — plan PROFESIONAL, ver docs/03-modelo-datos.md §18 y RN-28. */
@RestController
public class PromocionController {

    private final PromocionService promocionService;

    public PromocionController(PromocionService promocionService) {
        this.promocionService = promocionService;
    }

    @GetMapping("/api/promotions")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOCIONES_CONSULTAR + "') and @modulos.activo('PROMOCIONES')")
    public List<PromocionResponse> listar() {
        return promocionService.listar();
    }

    @GetMapping("/api/promotions/applicable")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOCIONES_CONSULTAR + "') and @modulos.activo('PROMOCIONES')")
    public List<PromocionResponse> vigentesParaVariante(@RequestParam Long variantId) {
        return promocionService.listarVigentesParaVariante(variantId);
    }

    @GetMapping("/api/promotions/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOCIONES_CONSULTAR + "') and @modulos.activo('PROMOCIONES')")
    public PromocionResponse obtener(@PathVariable Long id) {
        return promocionService.obtener(id);
    }

    @PostMapping("/api/promotions")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOCIONES_GESTIONAR + "') and @modulos.activo('PROMOCIONES')")
    public ResponseEntity<PromocionResponse> crear(@Valid @RequestBody PromocionRequest request) {
        PromocionResponse creada = promocionService.crear(request);
        return ResponseEntity.created(URI.create("/api/promotions/" + creada.id())).body(creada);
    }

    @PutMapping("/api/promotions/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOCIONES_GESTIONAR + "') and @modulos.activo('PROMOCIONES')")
    public PromocionResponse actualizar(@PathVariable Long id, @Valid @RequestBody PromocionRequest request) {
        return promocionService.actualizar(id, request);
    }

    @PatchMapping("/api/promotions/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.PROMOCIONES_GESTIONAR + "') and @modulos.activo('PROMOCIONES')")
    public PromocionResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return promocionService.cambiarEstado(id, request.status());
    }
}
