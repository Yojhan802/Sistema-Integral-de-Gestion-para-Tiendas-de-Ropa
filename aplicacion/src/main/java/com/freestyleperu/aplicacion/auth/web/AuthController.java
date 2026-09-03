package com.freestyleperu.aplicacion.auth.web;

import com.freestyleperu.aplicacion.auth.dto.ChangePasswordRequest;
import com.freestyleperu.aplicacion.auth.dto.LoginRequest;
import com.freestyleperu.aplicacion.auth.dto.LoginResponse;
import com.freestyleperu.aplicacion.auth.dto.RefreshRequest;
import com.freestyleperu.aplicacion.auth.dto.TokenResponse;
import com.freestyleperu.aplicacion.auth.dto.UsuarioActualResponse;
import com.freestyleperu.aplicacion.auth.service.AuthService;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @PostMapping("/api/auth/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/auth/me")
    public UsuarioActualResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return authService.me(currentUser.id());
    }

    @PostMapping("/api/auth/change-password")
    @PreAuthorize("hasAuthority('" + Permisos.USUARIOS_CAMBIAR_CONTRASENA + "')")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/complete-password-change")
    public ResponseEntity<Void> completeForcedPasswordChange(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.completeForcedPasswordChange(currentUser.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
