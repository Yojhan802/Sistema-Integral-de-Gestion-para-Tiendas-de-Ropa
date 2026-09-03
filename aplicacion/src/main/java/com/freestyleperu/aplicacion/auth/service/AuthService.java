package com.freestyleperu.aplicacion.auth.service;

import com.freestyleperu.aplicacion.auth.domain.RefreshToken;
import com.freestyleperu.aplicacion.auth.dto.LoginResponse;
import com.freestyleperu.aplicacion.auth.dto.TokenResponse;
import com.freestyleperu.aplicacion.auth.dto.UsuarioActualResponse;
import com.freestyleperu.aplicacion.auth.repository.RefreshTokenRepository;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.AutenticacionException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.exception.ReglaDeNegocioException;
import com.freestyleperu.aplicacion.shared.security.AccountLockProperties;
import com.freestyleperu.aplicacion.shared.security.JwtService;
import com.freestyleperu.aplicacion.shared.security.TokenHasher;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";
    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    private static final String TOKEN_TYPE = "Bearer";

    private final UsuarioRepository usuarioRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AccountLockProperties lockProperties;
    private final AuthService self;

    public AuthService(UsuarioRepository usuarioRepository, RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService, PasswordEncoder passwordEncoder, AuditService auditService,
            AccountLockProperties lockProperties, @Lazy AuthService self) {
        this.usuarioRepository = usuarioRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.lockProperties = lockProperties;
        this.self = self;
    }

    /**
     * Reintenta ante un deadlock transitorio de MySQL (confirmado con una prueba de carga
     * real: bajo login concurrente, InnoDB puede matar una de dos transacciones que compiten
     * por el lock de la misma fila de usuario — ver ALTA PERF-01 en la auditoría). Cada
     * reintento vuelve a ejecutar login() completo en una transacción nueva (@Retryable
     * envuelve a @Transactional, no al revés), así que es seguro repetirlo entero.
     */
    @Retryable(retryFor = CannotAcquireLockException.class, maxAttempts = 6,
            backoff = @Backoff(delay = 30, multiplier = 2, random = true))
    public LoginResponse login(String username, String rawPassword) {
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);

        if (usuario == null || !passwordEncoder.matches(rawPassword, usuario.getPasswordHash())) {
            if (usuario != null) {
                registrarIntentoFallido(usuario);
            }
            auditService.logAs(usuario == null ? null : usuario.getId(), username,
                    "LOGIN", "USUARIO", usuario == null ? null : usuario.getId(), null, null, AuditResult.FAILURE);
            throw new AutenticacionException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        if (usuario.getStatus() != UsuarioEstado.ACTIVE) {
            auditService.logAs(usuario.getId(), username, "LOGIN", "USUARIO", usuario.getId(), null, null, AuditResult.DENIED);
            throw new AutenticacionException("Esta cuenta no está activa. Contacta a un administrador.");
        }

        if (usuario.isBloqueadoTemporalmente()) {
            auditService.logAs(usuario.getId(), username, "LOGIN", "USUARIO", usuario.getId(), null, null, AuditResult.DENIED);
            throw new AutenticacionException("Cuenta bloqueada temporalmente por intentos fallidos. Intenta más tarde.");
        }

        usuario.setFailedAttempts((short) 0);
        usuario.setLockedUntil(null);
        usuario.setLastLoginAt(LocalDateTime.now());

        Set<String> authorities = authoritiesOf(usuario);
        String accessToken = jwtService.generateAccessToken(usuario.getId(), usuario.getUsername(), authorities, usuario.getTenantId());
        String rawRefreshToken = crearRefreshToken(usuario);

        auditService.logAs(usuario.getId(), username, "LOGIN", "USUARIO", usuario.getId(), null, null, AuditResult.SUCCESS);

        return new LoginResponse(accessToken, rawRefreshToken, TOKEN_TYPE, jwtService.getAccessTokenSeconds(),
                aRespuestaActual(usuario, authorities));
    }

    public TokenResponse refresh(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AutenticacionException("Token de actualización inválido"));

        if (!token.isValido()) {
            throw new AutenticacionException("Token de actualización inválido o expirado");
        }

        Usuario usuario = token.getUsuario();
        if (usuario.getStatus() != UsuarioEstado.ACTIVE) {
            throw new AutenticacionException("Esta cuenta no está activa");
        }

        token.setRevokedAt(LocalDateTime.now());
        String nuevoRawRefreshToken = crearRefreshToken(usuario);
        String accessToken = jwtService.generateAccessToken(usuario.getId(), usuario.getUsername(), authoritiesOf(usuario), usuario.getTenantId());

        return new TokenResponse(accessToken, nuevoRawRefreshToken, TOKEN_TYPE, jwtService.getAccessTokenSeconds());
    }

    public void logout(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                auditService.log("LOGOUT", "USUARIO", token.getUsuario().getId(), null, null, AuditResult.SUCCESS);
            }
        });
    }

    public UsuarioActualResponse me(Long usuarioId) {
        Usuario usuario = usuarioRepository.findWithRolesById(usuarioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", usuarioId));
        return aRespuestaActual(usuario, authoritiesOf(usuario));
    }

    public void changePassword(Long usuarioId, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", usuarioId));

        validarYActualizarPassword(usuario, currentPassword, newPassword);
    }

    public void completeForcedPasswordChange(Long usuarioId, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", usuarioId));

        if (!usuario.isMustChangePassword()) {
            throw new ReglaDeNegocioException("No hay un cambio obligatorio de contraseña pendiente");
        }

        validarYActualizarPassword(usuario, currentPassword, newPassword);
    }

    private void validarYActualizarPassword(Usuario usuario, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
            throw new AutenticacionException("La contraseña actual es incorrecta");
        }
        if (!PASSWORD_POLICY.matcher(newPassword).matches()) {
            throw new ReglaDeNegocioException("La nueva contraseña debe tener al menos 8 caracteres, con letras y números");
        }

        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuario.setMustChangePassword(false);
        refreshTokenRepository.revocarTodosDelUsuario(usuario.getId(), LocalDateTime.now());

        auditService.log("PASSWORD_CAMBIADO", "USUARIO", usuario.getId(), null, null, AuditResult.SUCCESS);
    }

    private void registrarIntentoFallido(Usuario usuario) {
        short intentos = (short) (usuario.getFailedAttempts() + 1);
        LocalDateTime lockedUntil = intentos >= lockProperties.getMaxFailedAttempts()
                ? LocalDateTime.now().plusMinutes(lockProperties.getLockDurationMinutes())
                : null;
        self.persistirIntentoFallido(usuario.getId(), intentos, lockedUntil);
    }

    private Set<String> authoritiesOf(Usuario usuario) {
        Set<String> authorities = new HashSet<>(usuario.permisosEfectivos());
        if (usuario.isPlatformOperator()) {
            authorities.add(com.freestyleperu.aplicacion.shared.security.Permisos.PLATAFORMA_EMPRESAS_GESTIONAR);
        }
        return Set.copyOf(authorities);
    }

    /**
     * Transacción independiente (REQUIRES_NEW): el intento fallido debe persistirse aunque
     * login() termine lanzando una excepción y revirtiendo su propia transacción. Solo tiene
     * efecto porque se invoca a través de {@code self} — una auto-invocación (this.metodo(...))
     * omitiría el proxy de Spring y la anotación @Transactional se ignoraría en silencio.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistirIntentoFallido(Long usuarioId, short intentos, LocalDateTime lockedUntil) {
        usuarioRepository.findById(usuarioId).ifPresent(u -> {
            u.setFailedAttempts(intentos);
            if (lockedUntil != null) {
                u.setLockedUntil(lockedUntil);
            }
        });
    }

    private String crearRefreshToken(Usuario usuario) {
        String raw = jwtService.generateRawRefreshToken();
        RefreshToken token = new RefreshToken();
        token.setUsuario(usuario);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(LocalDateTime.now().plusDays(jwtService.getRefreshTokenDays()));
        token.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
        return raw;
    }

    private UsuarioActualResponse aRespuestaActual(Usuario usuario, Set<String> authorities) {
        List<String> roles = usuario.getRoles().stream().map(Rol::getCode).sorted().toList();
        List<String> permissions = authorities.stream().sorted(Comparator.naturalOrder()).toList();
        return new UsuarioActualResponse(usuario.getId(), usuario.getUsername(), usuario.getFullName(),
                usuario.isMustChangePassword(), roles, permissions);
    }
}
