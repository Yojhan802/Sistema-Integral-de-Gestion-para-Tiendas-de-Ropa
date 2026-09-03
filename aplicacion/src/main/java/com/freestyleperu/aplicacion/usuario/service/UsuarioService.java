package com.freestyleperu.aplicacion.usuario.service;

import com.freestyleperu.aplicacion.configuracion.PlanGate;
import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.domain.UsuarioEstado;
import com.freestyleperu.aplicacion.usuario.dto.request.ActualizarUsuarioRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.CambiarEstadoUsuarioRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.CrearUsuarioRequest;
import com.freestyleperu.aplicacion.usuario.dto.response.PasswordTemporalResponse;
import com.freestyleperu.aplicacion.usuario.dto.response.UsuarioResponse;
import com.freestyleperu.aplicacion.usuario.event.UsuarioEstadoCambiadoEvent;
import com.freestyleperu.aplicacion.usuario.mapper.UsuarioMapper;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioService {

    private static final String ALFABETO_PASSWORD_TEMPORAL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlanGate planGate;
    private final SecureRandom secureRandom = new SecureRandom();

    public UsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            UsuarioMapper usuarioMapper, PasswordEncoder passwordEncoder, AuditService auditService,
            ApplicationEventPublisher eventPublisher, PlanGate planGate) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.planGate = planGate;
    }

    public Page<UsuarioResponse> listar(String search, UsuarioEstado status, Pageable pageable) {
        return usuarioRepository.buscar(search, status, pageable).map(usuarioMapper::toResponse);
    }

    public UsuarioResponse obtener(Long id) {
        return usuarioMapper.toResponse(buscarOFallar(id));
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request, Long currentUserId) {
        if (usuarioRepository.count() >= planGate.limiteUsuarios()) {
            throw new OperacionNoPermitidaException(
                    "Tu plan permite hasta " + planGate.limiteUsuarios() + " usuarios — contacta a soporte para ampliar tu plan");
        }
        if (usuarioRepository.existsByUsername(request.username())) {
            throw new RecursoDuplicadoException("El nombre de usuario " + request.username() + " ya está en uso");
        }
        if (request.email() != null && usuarioRepository.existsByEmail(request.email())) {
            throw new RecursoDuplicadoException("El correo " + request.email() + " ya está registrado");
        }
        if (request.dni() != null && usuarioRepository.existsByDni(request.dni())) {
            throw new RecursoDuplicadoException("El DNI " + request.dni() + " ya está registrado");
        }

        List<Rol> rolesSolicitados = resolverRoles(request.roleIds());
        validarTechoDeAsignacion(currentUserId, rolesSolicitados);

        Usuario usuario = new Usuario();
        usuario.setUsername(request.username());
        usuario.setEmail(request.email());
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setFullName(request.fullName());
        usuario.setDni(request.dni());
        usuario.setPhone(request.phone());
        usuario.setStatus(UsuarioEstado.ACTIVE);
        usuario.setMustChangePassword(true);
        usuario.setRoles(new HashSet<>(rolesSolicitados));

        Usuario guardado = usuarioRepository.save(usuario);
        auditService.log("USUARIO_CREADO", "USUARIO", guardado.getId(), null,
                new Object[] { guardado.getUsername(), guardado.getFullName() }, AuditResult.SUCCESS);
        return usuarioMapper.toResponse(guardado);
    }

    @Transactional
    public UsuarioResponse actualizar(Long id, ActualizarUsuarioRequest request, Long currentUserId) {
        Usuario usuario = buscarOFallar(id);
        if (request.email() != null && !request.email().equalsIgnoreCase(usuario.getEmail())
                && usuarioRepository.existsByEmail(request.email())) {
            throw new RecursoDuplicadoException("El correo " + request.email() + " ya está registrado");
        }
        if (request.dni() != null && !request.dni().equals(usuario.getDni())
                && usuarioRepository.existsByDni(request.dni())) {
            throw new RecursoDuplicadoException("El DNI " + request.dni() + " ya está registrado");
        }

        List<Rol> rolesSolicitados = resolverRoles(request.roleIds());
        validarTechoDeAsignacion(currentUserId, rolesSolicitados);

        usuario.setEmail(request.email());
        usuario.setFullName(request.fullName());
        usuario.setDni(request.dni());
        usuario.setPhone(request.phone());
        usuario.setRoles(new HashSet<>(rolesSolicitados));

        auditService.log("USUARIO_ACTUALIZADO", "USUARIO", usuario.getId(), null, request, AuditResult.SUCCESS);
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(Long id, CambiarEstadoUsuarioRequest request) {
        Usuario usuario = buscarOFallar(id);
        usuario.setStatus(request.status());
        if (request.status() != UsuarioEstado.ACTIVE) {
            usuario.setFailedAttempts((short) 0);
            usuario.setLockedUntil(null);
        }
        auditService.log("USUARIO_CAMBIO_ESTADO", "USUARIO", usuario.getId(), null, request.status(), AuditResult.SUCCESS);
        eventPublisher.publishEvent(new UsuarioEstadoCambiadoEvent(usuario.getId(), request.status()));
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public PasswordTemporalResponse resetPassword(Long id) {
        Usuario usuario = buscarOFallar(id);
        String temporal = generarPasswordTemporal();
        usuario.setPasswordHash(passwordEncoder.encode(temporal));
        usuario.setMustChangePassword(true);
        auditService.log("USUARIO_PASSWORD_RESETEADO", "USUARIO", usuario.getId(), null, null, AuditResult.SUCCESS);
        eventPublisher.publishEvent(new UsuarioEstadoCambiadoEvent(usuario.getId(), usuario.getStatus()));
        return new PasswordTemporalResponse(temporal);
    }

    private List<Rol> resolverRoles(List<Long> roleIds) {
        List<Rol> roles = rolRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new RecursoNoEncontradoException("Uno o más roles no existen");
        }
        return roles;
    }

    /** RN-25: solo se pueden asignar roles cuyo hierarchyLevel no supere el más alto entre los roles propios de quien crea. */
    private void validarTechoDeAsignacion(Long currentUserId, List<Rol> rolesSolicitados) {
        Usuario actor = buscarOFallar(currentUserId);
        int techo = actor.getRoles().stream().mapToInt(Rol::getHierarchyLevel).max().orElse(0);
        for (Rol rol : rolesSolicitados) {
            if (rol.getHierarchyLevel() > techo) {
                throw new OperacionNoPermitidaException(
                        "No puedes asignar el rol '" + rol.getName() + "' — supera tu nivel de autorización");
            }
        }
    }

    private String generarPasswordTemporal() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALFABETO_PASSWORD_TEMPORAL.charAt(secureRandom.nextInt(ALFABETO_PASSWORD_TEMPORAL.length())));
        }
        return sb.toString();
    }

    private Usuario buscarOFallar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", id));
    }
}
