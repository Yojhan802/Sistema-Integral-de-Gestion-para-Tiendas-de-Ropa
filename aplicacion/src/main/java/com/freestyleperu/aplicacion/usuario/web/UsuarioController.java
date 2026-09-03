package com.freestyleperu.aplicacion.usuario.web;

import com.freestyleperu.aplicacion.shared.dto.PageResponse;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.dto.request.ActualizarUsuarioRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.CambiarEstadoUsuarioRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.CrearUsuarioRequest;
import com.freestyleperu.aplicacion.usuario.dto.response.PasswordTemporalResponse;
import com.freestyleperu.aplicacion.usuario.dto.response.UsuarioResponse;
import com.freestyleperu.aplicacion.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/api/users")
    @PreAuthorize("hasAuthority('" + Permisos.USUARIOS_CONSULTAR + "')")
    public PageResponse<UsuarioResponse> listar(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UsuarioEstado status,
            Pageable pageable) {
        return PageResponse.of(usuarioService.listar(search, status, pageable), it -> it);
    }

    @GetMapping("/api/users/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.USUARIOS_CONSULTAR + "')")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarioService.obtener(id);
    }

    @PostMapping("/api/users")
    @PreAuthorize("hasAuthority('" + Permisos.USUARIOS_CREAR + "')")
    public ResponseEntity<UsuarioResponse> crear(
            @Valid @RequestBody CrearUsuarioRequest request, @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UsuarioResponse creado = usuarioService.crear(request, currentUser.id());
        return ResponseEntity.created(URI.create("/api/users/" + creado.id())).body(creado);
    }

    @PutMapping("/api/users/{id}")
    @PreAuthorize("hasAuthority('" + Permisos.USUARIOS_EDITAR + "')")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarUsuarioRequest request,
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return usuarioService.actualizar(id, request, currentUser.id());
    }

    @PatchMapping("/api/users/{id}/status")
    @PreAuthorize("hasAuthority('" + Permisos.USUARIOS_BLOQUEAR + "')")
    public UsuarioResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoUsuarioRequest request) {
        return usuarioService.cambiarEstado(id, request);
    }

    @PostMapping("/api/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('" + Permisos.USUARIOS_RESETEAR_CONTRASENA + "')")
    public PasswordTemporalResponse resetearPassword(@PathVariable Long id) {
        return usuarioService.resetPassword(id);
    }
}
