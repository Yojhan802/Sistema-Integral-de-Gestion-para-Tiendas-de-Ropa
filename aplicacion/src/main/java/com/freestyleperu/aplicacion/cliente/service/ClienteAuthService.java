package com.freestyleperu.aplicacion.cliente.service;

import com.freestyleperu.aplicacion.cliente.domain.Customer;
import com.freestyleperu.aplicacion.cliente.domain.CustomerRefreshToken;
import com.freestyleperu.aplicacion.cliente.dto.request.RegistroClienteRequest;
import com.freestyleperu.aplicacion.cliente.dto.response.ClienteActualResponse;
import com.freestyleperu.aplicacion.cliente.dto.response.ClienteAuthResponse;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRefreshTokenRepository;
import com.freestyleperu.aplicacion.cliente.repository.CustomerRepository;
import com.freestyleperu.aplicacion.shared.domain.EstadoGeneral;
import com.freestyleperu.aplicacion.shared.exception.AutenticacionException;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.security.JwtService;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.shared.security.TokenHasher;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autenticación de clientes de la tienda online — deliberadamente aparte de
 * {@code auth.service.AuthService} (que es de staff): reutiliza el mismo
 * {@link JwtService}, pero el token de un cliente lleva {@code ROLE_CUSTOMER}
 * como única autoridad, nunca un permiso de staff (docs/03 y 05, Fase 2).
 */
@Service
@Transactional
public class ClienteAuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    private final CustomerRepository customerRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public ClienteAuthService(CustomerRepository customerRepository, CustomerRefreshTokenRepository refreshTokenRepository,
            JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.customerRepository = customerRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    /** Si ya existe un cliente con ese correo (de una compra en tienda física) sin contraseña, se le asigna la cuenta a ese registro. */
    public ClienteAuthResponse registrar(RegistroClienteRequest request) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        if (customer != null && customer.getPasswordHash() != null) {
            throw new RecursoDuplicadoException("Ya existe una cuenta con el correo " + request.email());
        }
        if (customer == null) {
            customer = new Customer();
            customer.setEmail(request.email());
            customer.setStatus(EstadoGeneral.ACTIVE);
        }
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        Customer guardado = customerRepository.save(customer);
        return construirRespuesta(guardado);
    }

    public ClienteAuthResponse login(String email, String rawPassword) {
        Customer customer = customerRepository.findByEmailIgnoreCase(email).orElse(null);
        if (customer == null || customer.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, customer.getPasswordHash())) {
            throw new AutenticacionException(MENSAJE_CREDENCIALES_INVALIDAS);
        }
        if (customer.getStatus() != EstadoGeneral.ACTIVE) {
            throw new AutenticacionException("Esta cuenta no está activa");
        }
        return construirRespuesta(customer);
    }

    public ClienteAuthResponse refresh(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        CustomerRefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AutenticacionException("Token de actualización inválido"));
        if (!token.isValido()) {
            throw new AutenticacionException("Token de actualización inválido o expirado");
        }

        Customer customer = token.getCustomer();
        if (customer.getStatus() != EstadoGeneral.ACTIVE) {
            throw new AutenticacionException("Esta cuenta no está activa");
        }

        token.setRevokedAt(LocalDateTime.now());
        return construirRespuesta(customer);
    }

    private ClienteAuthResponse construirRespuesta(Customer customer) {
        String accessToken = jwtService.generateAccessToken(
                customer.getId(), customer.getEmail(), Set.of(Permisos.ROLE_CUSTOMER), customer.getTenantId());
        String rawRefreshToken = crearRefreshToken(customer);
        ClienteActualResponse actual = new ClienteActualResponse(
                customer.getId(), customer.getFullName(), customer.getEmail(), customer.getPhone());
        return new ClienteAuthResponse(accessToken, rawRefreshToken, TOKEN_TYPE, jwtService.getAccessTokenSeconds(), actual);
    }

    private String crearRefreshToken(Customer customer) {
        String raw = jwtService.generateRawRefreshToken();
        CustomerRefreshToken token = new CustomerRefreshToken();
        token.setCustomer(customer);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(LocalDateTime.now().plusDays(jwtService.getRefreshTokenDays()));
        token.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
        return raw;
    }
}
