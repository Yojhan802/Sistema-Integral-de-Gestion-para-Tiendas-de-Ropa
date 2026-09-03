package com.freestyleperu.aplicacion.usuario.web;

import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.usuario.dto.request.ActualizarRolRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.AsignarPermisosRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.CrearRolRequest;
import com.freestyleperu.aplicacion.usuario.dto.response.RolResponse;
import com.freestyleperu.aplicacion.usuario.service.RolService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los GET son más permisivos que el resto a propósito: crear/editar usuarios
 * exige poder listar los roles para armar el selector (ver usuario-form.js),
 * aunque quien crea usuarios no tenga permiso para administrar roles en sí
 * (ROLES_GESTIONAR sigue siendo, deliberadamente, solo de Administrador).
 */
@RestController
public class RolController {

    private static final String PUEDE_VER =
            "hasAuthority('" + Permisos.ROLES_GESTIONAR + "') or hasAuthority('" + Permisos.USUARIOS_CREAR
                    + "') or hasAuthority('" + Permisos.USUARIOS_EDITAR + "')";

    private final RolService rolService;

    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    @GetMapping("/api/roles")
    @PreAuthorize(PUEDE_VER)
    public List<RolResponse> listar() {
        return rolService.listar();
    }

    @GetMapping("/api/roles/{id}")
    @PreAuthorize(PUEDE_VER)
    public RolResponse obtener(@PathVariable Long id) {
        return rolService.obtener(id);
    }

    @PostMapping("/api/roles")
    @PreAuthorize("hasAuthority('" + Permisos.ROLES_GESTIONAR + "')")
    public ResponseEntity<RolResponse> crear(@Valid @RequestBody CrearRolRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        RolResponse creado = rolService.crear(request, currentUser.id());
        return ResponseEntity.created(URI.create("/api/roles/" + creado.id())).body(creado);
    }

    @PutMapping("/api/roles/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.ROLES_GESTIONAR + "')")
    public RolResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarRolRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return rolService.actualizar(id, request, currentUser.id());
    }

    @PutMapping("/api/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('" + Permisos.ROLES_GESTIONAR + "')")
    public RolResponse actualizarPermisos(@PathVariable Long id, @Valid @RequestBody AsignarPermisosRequest request) {
        return rolService.actualizarPermisos(id, request);
    }
}
