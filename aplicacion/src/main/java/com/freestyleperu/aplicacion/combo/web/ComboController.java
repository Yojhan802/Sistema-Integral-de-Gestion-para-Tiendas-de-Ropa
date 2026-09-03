package com.freestyleperu.aplicacion.combo.web;

import com.freestyleperu.aplicacion.combo.dto.request.ComboRequest;
import com.freestyleperu.aplicacion.combo.dto.response.ComboResponse;
import com.freestyleperu.aplicacion.combo.service.ComboService;
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
import org.springframework.web.bind.annotation.RestController;

/** Combos — plan PROFESIONAL, ver docs/03-modelo-datos.md §18 y RN-28. */
@RestController
public class ComboController {

    private final ComboService comboService;

    public ComboController(ComboService comboService) {
        this.comboService = comboService;
    }

    @GetMapping("/api/combos")
    @PreAuthorize("hasAuthority('" + Permisos.COMBOS_CONSULTAR + "') and @modulos.activo('COMBOS')")
    public List<ComboResponse> listar() {
        return comboService.listar();
    }

    @GetMapping("/api/combos/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.COMBOS_CONSULTAR + "') and @modulos.activo('COMBOS')")
    public ComboResponse obtener(@PathVariable Long id) {
        return comboService.obtener(id);
    }

    @PostMapping("/api/combos")
    @PreAuthorize("hasAuthority('" + Permisos.COMBOS_GESTIONAR + "') and @modulos.activo('COMBOS')")
    public ResponseEntity<ComboResponse> crear(@Valid @RequestBody ComboRequest request) {
        ComboResponse creado = comboService.crear(request);
        return ResponseEntity.created(URI.create("/api/combos/" + creado.id())).body(creado);
    }

    @PutMapping("/api/combos/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.COMBOS_GESTIONAR + "') and @modulos.activo('COMBOS')")
    public ComboResponse actualizar(@PathVariable Long id, @Valid @RequestBody ComboRequest request) {
        return comboService.actualizar(id, request);
    }

    @PatchMapping("/api/combos/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.COMBOS_GESTIONAR + "') and @modulos.activo('COMBOS')")
    public ComboResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        return comboService.cambiarEstado(id, request.status());
    }
}
