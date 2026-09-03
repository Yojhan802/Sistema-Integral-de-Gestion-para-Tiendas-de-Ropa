package com.freestyleperu.aplicacion.usuario.service;

import com.freestyleperu.aplicacion.shared.audit.AuditResult;
import com.freestyleperu.aplicacion.shared.audit.AuditService;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.exception.RecursoDuplicadoException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import com.freestyleperu.aplicacion.shared.security.Permisos;
import com.freestyleperu.aplicacion.usuario.domain.Permiso;
import com.freestyleperu.aplicacion.usuario.domain.Rol;
import com.freestyleperu.aplicacion.usuario.dto.request.ActualizarRolRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.AsignarPermisosRequest;
import com.freestyleperu.aplicacion.usuario.dto.request.CrearRolRequest;
import com.freestyleperu.aplicacion.usuario.dto.response.RolResponse;
import com.freestyleperu.aplicacion.usuario.domain.Usuario;
import com.freestyleperu.aplicacion.usuario.mapper.RolMapper;
import com.freestyleperu.aplicacion.usuario.repository.PermisoRepository;
import com.freestyleperu.aplicacion.usuario.repository.RolRepository;
import com.freestyleperu.aplicacion.usuario.repository.UsuarioRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolMapper rolMapper;
    private final AuditService auditService;

    public RolService(RolRepository rolRepository, PermisoRepository permisoRepository,
            UsuarioRepository usuarioRepository, RolMapper rolMapper, AuditService auditService) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
        this.usuarioRepository = usuarioRepository;
        this.rolMapper = rolMapper;
        this.auditService = auditService;
    }

    public List<RolResponse> listar() {
        return rolRepository.findAllByOrderByNameAsc().stream().map(rolMapper::toResponse).toList();
    }

    public RolResponse obtener(Long id) {
        return rolMapper.toResponse(buscarOFallar(id));
    }

    @Transactional
    public RolResponse crear(CrearRolRequest request, Long currentUserId) {
        if (rolRepository.existsByCode(request.code())) {
            throw new RecursoDuplicadoException("Ya existe un rol con el código " + request.code());
        }
        short hierarchyLevel = request.hierarchyLevel() == null ? (short) 0 : request.hierarchyLevel().shortValue();
        validarTechoDeAsignacion(currentUserId, hierarchyLevel);
        Rol rol = new Rol();
        rol.setCode(request.code());
        rol.setName(request.name());
        rol.setDescription(request.description());
        rol.setSystem(false);
        rol.setHierarchyLevel(hierarchyLevel);
        permisoRepository.findByCode(Permisos.USUARIOS_CAMBIAR_CONTRASENA)
                .ifPresent(permiso -> rol.setPermisos(new HashSet<>(Set.of(permiso))));
        Rol guardado = rolRepository.save(rol);
        auditService.log("ROL_CREADO", "ROL", guardado.getId(), null, request, AuditResult.SUCCESS);
        return rolMapper.toResponse(guardado);
    }

    @Transactional
    public RolResponse actualizar(Long id, ActualizarRolRequest request, Long currentUserId) {
        Rol rol = buscarOFallar(id);
        rol.setName(request.name());
        rol.setDescription(request.description());
        if (request.hierarchyLevel() != null) {
            short hierarchyLevel = request.hierarchyLevel().shortValue();
            validarTechoDeAsignacion(currentUserId, hierarchyLevel);
            rol.setHierarchyLevel(hierarchyLevel);
        }
        auditService.log("ROL_ACTUALIZADO", "ROL", rol.getId(), null, request, AuditResult.SUCCESS);
        return rolMapper.toResponse(rol);
    }

    /** RN-25 aplicada a roles: no se puede crear/editar un rol con un techo de asignación por encima del propio. */
    private void validarTechoDeAsignacion(Long currentUserId, short hierarchyLevel) {
        Usuario actor = usuarioRepository.findWithRolesById(currentUserId)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Usuario", currentUserId));
        int techo = actor.getRoles().stream().mapToInt(Rol::getHierarchyLevel).max().orElse(0);
        if (hierarchyLevel > techo) {
            throw new OperacionNoPermitidaException(
                    "No puedes asignar un techo de asignación (" + hierarchyLevel + ") superior a tu propio nivel de autorización");
        }
    }

    @Transactional
    public RolResponse actualizarPermisos(Long id, AsignarPermisosRequest request) {
        Rol rol = buscarOFallar(id);
        if (rol.isSystem() && "ADMINISTRADOR".equals(rol.getCode())) {
            throw new OperacionNoPermitidaException("No se pueden modificar los permisos del rol Administrador");
        }
        List<Permiso> permisos = permisoRepository.findAllById(request.permissionIds());
        if (permisos.size() != request.permissionIds().size()) {
            throw new RecursoNoEncontradoException("Uno o más permisos no existen");
        }
        if (permisos.stream().anyMatch(permiso -> Permisos.USUARIOS_RESETEAR_CONTRASENA.equals(permiso.getCode()))
                && !"ADMINISTRADOR".equals(rol.getCode())) {
            throw new OperacionNoPermitidaException(
                    "El permiso para resetear contrasenas esta reservado al rol Administrador");
        }
        permisoRepository.findByCode(Permisos.USUARIOS_CAMBIAR_CONTRASENA).ifPresent(permiso -> {
            if (permisos.stream().noneMatch(actual -> actual.getCode().equals(permiso.getCode()))) {
                permisos.add(permiso);
            }
        });
        rol.setPermisos(new HashSet<>(permisos));
        auditService.log("ROL_PERMISOS_ACTUALIZADOS", "ROL", rol.getId(), null,
                Set.copyOf(request.permissionIds()), AuditResult.SUCCESS);
        return rolMapper.toResponse(rol);
    }

    private Rol buscarOFallar(Long id) {
        return rolRepository.findWithPermisosById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("Rol", id));
    }
}
