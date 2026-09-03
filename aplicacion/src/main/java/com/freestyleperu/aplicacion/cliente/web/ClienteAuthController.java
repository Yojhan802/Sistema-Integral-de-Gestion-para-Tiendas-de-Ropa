package com.freestyleperu.aplicacion.cliente.web;

import com.freestyleperu.aplicacion.auth.dto.RefreshRequest;
import com.freestyleperu.aplicacion.cliente.dto.request.LoginClienteRequest;
import com.freestyleperu.aplicacion.cliente.dto.request.RegistroClienteRequest;
import com.freestyleperu.aplicacion.cliente.dto.response.ClienteActualResponse;
import com.freestyleperu.aplicacion.cliente.dto.response.ClienteAuthResponse;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.cliente.service.ClienteAuthService;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.security.AuthenticatedUser;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación de clientes de la tienda — todo bajo /api/store/auth, público salvo /me.
 * Gateado por plan igual que el resto de /api/store/** (catálogo, pedidos): no tiene sentido
 * dejar registrarse/loguearse a un cliente en una instalación que no tiene tienda online.
 */
@RestController
@PreAuthorize("@modulos.activo('TIENDA')")
public class ClienteAuthController {

    private final ClienteAuthService clienteAuthService;
    private final CustomerRepository customerRepository;

    public ClienteAuthController(ClienteAuthService clienteAuthService, CustomerRepository customerRepository) {
        this.clienteAuthService = clienteAuthService;
        this.customerRepository = customerRepository;
    }

    @PostMapping("/api/store/auth/register")
    public ClienteAuthResponse registrar(@Valid @RequestBody RegistroClienteRequest request) {
        return clienteAuthService.registrar(request);
    }

    @PostMapping("/api/store/auth/login")
    public ClienteAuthResponse login(@Valid @RequestBody LoginClienteRequest request) {
        return clienteAuthService.login(request.email(), request.password());
    }

    @PostMapping("/api/store/auth/refresh")
    public ClienteAuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return clienteAuthService.refresh(request.refreshToken());
    }

    @GetMapping("/api/store/auth/me")
    @PreAuthorize("hasAuthority('" + Permisos.ROLE_CUSTOMER + "') and @modulos.activo('TIENDA')")
    public ClienteActualResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        var customer = customerRepository.findById(currentUser.id())
                .orElseThrow(() -> RecursoNoEncontradoException.de("Cliente", currentUser.id()));
        return new ClienteActualResponse(customer.getId(), customer.getFullName(), customer.getEmail(), customer.getPhone());
    }
}
