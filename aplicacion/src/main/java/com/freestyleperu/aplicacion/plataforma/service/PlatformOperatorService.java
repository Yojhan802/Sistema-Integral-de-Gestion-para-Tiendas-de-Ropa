package com.freestyleperu.aplicacion.plataforma.service;

import com.freestyleperu.aplicacion.plataforma.dto.response.UsuarioEmpresaResponse;
import com.freestyleperu.aplicacion.plataforma.repository.PlatformTenantRepository;
import com.freestyleperu.aplicacion.shared.exception.OperacionNoPermitidaException;
import com.freestyleperu.aplicacion.shared.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concede o retira la condición de operador de plataforma, que es lo que habilita el
 * módulo Empresas. Hasta ahora solo podía cambiarse con un UPDATE a mano en la base.
 *
 * <p>No es un permiso de rol a propósito: un Administrador de tienda no debe poder darse
 * de alta empresas a sí mismo, así que la marca vive en el usuario y solo la mueve quien
 * ya es operador.
 */
@Service
@Transactional(readOnly = true)
public class PlatformOperatorService {

    private final PlatformTenantRepository repository;

    public PlatformOperatorService(PlatformTenantRepository repository) {
        this.repository = repository;
    }

    public List<UsuarioEmpresaResponse> usuariosDe(Long tenantId) {
        return repository.listarUsuariosDe(tenantId);
    }

    @Transactional
    public void cambiar(Long usuarioId, boolean operador, Long actorId) {
        if (!repository.existeUsuario(usuarioId)) {
            throw RecursoNoEncontradoException.de("Usuario", usuarioId);
        }
        if (!operador) {
            // Quitarse el propio acceso deja al operador fuera del módulo sin forma de
            // volver a entrar; y quitar el último deja la plataforma sin quien la administre.
            if (usuarioId.equals(actorId)) {
                throw new OperacionNoPermitidaException("No puedes quitarte a ti mismo el acceso a Empresas");
            }
            if (repository.esOperador(usuarioId) && repository.contarOperadores() <= 1) {
                throw new OperacionNoPermitidaException("Debe quedar al menos un operador de plataforma");
            }
        }
        repository.actualizarOperador(usuarioId, operador);
    }
}
